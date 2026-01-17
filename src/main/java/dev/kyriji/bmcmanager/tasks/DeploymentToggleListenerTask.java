package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameServerManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import redis.clients.jedis.JedisPubSub;

public class DeploymentToggleListenerTask {
	private static final String DEPLOYMENT_RESTART_CHANNEL = "deployment-restart";

	public DeploymentToggleListenerTask() {
		GameServerManager gameServerManager = BMCManager.gameServerManager;

		// Listen for restart events
		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				try {
					String deploymentName = message.trim();

					GameServerWrapper<?> wrapper = gameServerManager.getGameServer(deploymentName);
					if (wrapper == null) {
						System.err.println("GameServer not found for restart: " + deploymentName);
						return;
					}

					System.out.println("Restarting deployment: " + deploymentName);

					// Delete all pods
					deleteAllPods(wrapper);

					System.out.println("Restart initiated for deployment: " + deploymentName);
				} catch (Exception e) {
					System.err.println("Error processing restart message: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}, DEPLOYMENT_RESTART_CHANNEL)).start();
	}

	private void deleteAllPods(GameServerWrapper<?> wrapper) {
		wrapper.fetchInstances();
		String namespace = wrapper.getGameServer().getMetadata().getNamespace();

		for (Instance instance : wrapper.getInstances()) {
			try {
				// Mark as stopping in Redis first
				instance.setState(InstanceState.STOPPING);
				RedisManager.get().updateInstance(instance);

				// Delete the pod
				BMCManager.kubernetesClient.pods()
					.inNamespace(namespace)
					.withName(instance.getPodName())
					.delete();
				System.out.println("Deleted pod (restart): " + instance.getPodName());
			} catch (Exception e) {
				System.err.println("Failed to delete pod " + instance.getPodName() + ": " + e.getMessage());
			}
		}
	}
}
