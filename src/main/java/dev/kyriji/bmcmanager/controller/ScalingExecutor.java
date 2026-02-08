package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.controllers.ShutdownNegotiationManager;
import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import dev.kyriji.bmcmanager.objects.ScalingDecision;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

public class ScalingExecutor {
	private final KubernetesClient client;
	private final PodBuilder podBuilder;

	public ScalingExecutor(KubernetesClient client) {
		this.client = client;
		this.podBuilder = new PodBuilder();
	}

	public void executeScaling(ScalingDecision decision, GameServer gameServer, GameServerWrapper<?> wrapper) {
		if (decision.getAction() == ScaleResult.NO_CHANGE) {
			return;
		}

		String namespace = gameServer.getMetadata().getNamespace();

		if (decision.getAction() == ScaleResult.UP) {
			scaleUp(decision, gameServer, namespace, wrapper);
		} else if (decision.getAction() == ScaleResult.DOWN) {
			scaleDown(decision, namespace, wrapper);
		}
	}

	private void scaleUp(ScalingDecision decision, GameServer gameServer, String namespace, GameServerWrapper<?> wrapper) {
		// Create pods directly - no Deployment replica manipulation needed
		int podsToCreate = decision.getTargetReplicas() - decision.getCurrentReplicas();

		for (int i = 0; i < podsToCreate; i++) {
			try {
				Pod pod = podBuilder.buildPod(gameServer);
				client.pods()
					.inNamespace(namespace)
					.resource(pod)
					.create();
				System.out.println("Created pod: " + pod.getMetadata().getName() + " for GameServer: " + gameServer.getMetadata().getName());
			} catch (Exception e) {
				System.err.println("Failed to create pod for " + gameServer.getMetadata().getName() + ": " + e.getMessage());
			}
		}

		// Update cooldown timestamp
		wrapper.setLastScaleUp(System.currentTimeMillis());
	}

	private void scaleDown(ScalingDecision decision, String namespace, GameServerWrapper<?> wrapper) {
		// CRITICAL: We manually delete specific pods to control WHICH instances are removed
		// (e.g., those with fewest players). This gives us absolute control.

		// NEW: Use shutdown negotiation instead of immediate deletion
		// This allows instances to gracefully drain players before shutdown

		// Step 1: Propose shutdown to selected instances
		// ShutdownNegotiationManager will:
		//   - Set instance state to BLOCKED (prevents new players)
		//   - Send shutdown proposal to instance
		//   - Handle responses (ACCEPT/DELAY/VETO)
		//   - Issue final shutdown when ready (sets STOPPING state)
		//   - InstanceListenerTask will then delete the pod
		for (Instance instance : decision.getPodsToDelete()) {
			String token = ShutdownNegotiationManager.get().proposeShutdown(instance, "scale_down");
			System.out.println("Proposed graceful shutdown for pod: " + instance.getPodName() +
			                   " (Token: " + token + ")");
		}

		// Step 2: Update cooldown timestamp
		// Note: Pods won't be deleted immediately, but the scale-down action is considered complete
		wrapper.setLastScaleDown(System.currentTimeMillis());
	}

	public int getCurrentPodCount(GameServer gameServer) {
		try {
			return client.pods()
				.inNamespace(gameServer.getMetadata().getNamespace())
				.withLabel("app", gameServer.getMetadata().getName())
				.list()
				.getItems()
				.size();
		} catch (Exception e) {
			System.err.println("Failed to count pods for " + gameServer.getMetadata().getName() + ": " + e.getMessage());
			return 0;
		}
	}
}
