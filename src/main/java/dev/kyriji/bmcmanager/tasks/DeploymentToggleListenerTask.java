package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameServerManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.controllers.ShutdownNegotiationManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import redis.clients.jedis.JedisPubSub;

public class DeploymentToggleListenerTask {
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
		}, RedisChannel.DEPLOYMENT_RESTART.getRef())).start();
	}

	private void deleteAllPods(GameServerWrapper<?> wrapper) {
		wrapper.fetchInstances();

		// Use shutdown negotiation for deployment restart
		// Short deadline (30 seconds) since this is an explicit restart command
		for (Instance instance : wrapper.getInstances()) {
			String token = ShutdownNegotiationManager.get().proposeShutdown(
				instance, "deployment_restart", 30);
			System.out.println("Proposed graceful shutdown for pod (deployment restart): " +
			                   instance.getPodName() + " (Token: " + token + ")");
		}
	}
}
