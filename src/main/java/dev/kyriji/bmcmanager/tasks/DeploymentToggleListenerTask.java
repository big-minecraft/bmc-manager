package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameServerManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import redis.clients.jedis.JedisPubSub;

public class DeploymentToggleListenerTask {
	public DeploymentToggleListenerTask() {
		GameServerManager gameServerManager = BMCManager.gameServerManager;

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				try {
					String[] parts = message.split(":");
					if (parts.length != 2) {
						System.err.println("Invalid toggle message format: " + message);
						return;
					}

					String deploymentName = parts[0];
					boolean enabled = Boolean.parseBoolean(parts[1]);

					GameServerWrapper<?> wrapper = gameServerManager.getGameServer(deploymentName);
					if (wrapper == null) {
						System.err.println("GameServer not found for toggle: " + deploymentName);
						return;
					}

					boolean wasEnabled = wrapper.isEnabled();
					wrapper.setEnabled(enabled);
					System.out.println("Toggled deployment " + deploymentName + " to " + (enabled ? "enabled" : "disabled"));

					// If toggling OFF, delete all pods for this deployment
					if (wasEnabled && !enabled) deleteAllPods(wrapper);
				} catch (Exception e) {
					System.err.println("Error processing toggle message: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}, RedisChannel.DEPLOYMENT_TOGGLED.getRef())).start();
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
				System.out.println("Deleted pod (toggle off): " + instance.getPodName());
			} catch (Exception e) {
				System.err.println("Failed to delete pod " + instance.getPodName() + ": " + e.getMessage());
			}
		}
	}
}
