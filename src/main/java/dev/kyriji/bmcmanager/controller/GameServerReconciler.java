package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.logic.ScalingLogic;
import dev.kyriji.bmcmanager.objects.*;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.lang.reflect.Type;

public class GameServerReconciler {
	private final KubernetesClient client;
	private final ScalingLogic scalingLogic;
	private final ScalingExecutor scalingExecutor;

	public GameServerReconciler(KubernetesClient client) {
		this.client = client;
		this.scalingLogic = new ScalingLogic();
		this.scalingExecutor = new ScalingExecutor(client);
	}

	public ReconcileResult reconcile(ReconcileRequest request) {
		try {
			// 1. Fetch GameServer from Kubernetes
			GameServer gameServer = fetchGameServer(request);
			if (gameServer == null) {
				// Resource not found, don't requeue
				return ReconcileResult.noRequeue();
			}

			// 2. Get the GameServerWrapper from the registry
			GameServerWrapper<?> wrapper = BMCManager.gameServerManager.getGameServer(request.getName());
			if (wrapper == null) {
				// Not registered yet, requeue to try again later
				return ReconcileResult.requeueAfter(5000);
			}

			// 3. Query Redis for current enabled state
			String enabledStr = RedisManager.get().hget("deployment:" + request.getName(), "enabled");
			boolean enabled = enabledStr == null || Boolean.parseBoolean(enabledStr);
			boolean wasEnabled = wrapper.isEnabled();
			wrapper.setEnabled(enabled);

			// 4. If transitioning to disabled, delete all pods
			if (wasEnabled && !enabled) {
				System.out.println("Deployment " + request.getName() + " disabled, deleting all pods");
				deleteAllPods(wrapper, gameServer.getMetadata().getNamespace());
				return ReconcileResult.requeueAfter(5000);
			}

			// 5. Skip scaling if disabled
			if (!enabled) {
				return ReconcileResult.requeueAfter(5000);
			}

			// 6. Only handle MinecraftInstance game servers for scaling
			Type instanceType = wrapper.getInstanceType();
			if (!MinecraftInstance.class.equals(instanceType)) {
				return ReconcileResult.noRequeue();
			}

			@SuppressWarnings("unchecked")
			GameServerWrapper<MinecraftInstance> minecraftWrapper = (GameServerWrapper<MinecraftInstance>) wrapper;

			// 7. Fetch latest instance data from Redis
			minecraftWrapper.fetchInstances();

			// 8. Get current pod count owned by this GameServer
			int currentPodCount = scalingExecutor.getCurrentPodCount(gameServer);

			// 9. Determine scaling action
			ScalingDecision decision = scalingLogic.determineScalingAction(minecraftWrapper, currentPodCount);

			// 10. Execute scaling if needed
			if (decision.getAction() != dev.kyriji.bmcmanager.enums.ScaleResult.NO_CHANGE) {
				scalingExecutor.executeScaling(decision, gameServer, wrapper);
				System.out.println("Scaled " + request.getName() + ": " + decision);
			}

			// 11. Always requeue after 5 seconds for periodic checks
			return ReconcileResult.requeueAfter(5000);

		} catch (Exception e) {
			// Log error and requeue after longer delay
			System.err.println("Error reconciling " + request + ": " + e.getMessage());
			e.printStackTrace();
			return ReconcileResult.requeueAfter(10000);
		}
	}

	private GameServer fetchGameServer(ReconcileRequest request) {
		try {
			return client.resources(GameServer.class)
				.inNamespace(request.getNamespace())
				.withName(request.getName())
				.get();
		} catch (Exception e) {
			System.err.println("Failed to fetch GameServer " + request + ": " + e.getMessage());
		}
		return null;
	}

	private void deleteAllPods(GameServerWrapper<?> wrapper, String namespace) {
		wrapper.fetchInstances();

		for (Instance instance : wrapper.getInstances()) {
			try {
				// Mark as stopping in Redis first
				instance.setState(InstanceState.STOPPING);
				RedisManager.get().updateInstance(instance);

				// Delete the pod
				client.pods()
					.inNamespace(namespace)
					.withName(instance.getPodName())
					.delete();
				System.out.println("Deleted pod (disabled): " + instance.getPodName());
			} catch (Exception e) {
				System.err.println("Failed to delete pod " + instance.getPodName() + ": " + e.getMessage());
			}
		}
	}
}
