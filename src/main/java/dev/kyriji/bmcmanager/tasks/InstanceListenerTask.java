package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.DeploymentManager;
import dev.kyriji.bmcmanager.controllers.QueueManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.kyriji.bmcmanager.objects.Game;
import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.UUID;

public class InstanceListenerTask {
	Gson gson = new Gson();

	public InstanceListenerTask() {
		DeploymentManager deploymentManager = BMCManager.deploymentManager;

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");

				String instanceIP = parts[0];
				String stateString = parts[1];

				Instance instance = BMCManager.instanceManager.getFromIP(instanceIP);
				if(instance == null) return;
				DeploymentWrapper<? extends Instance> deployment = deploymentManager.getDeployment(instance.getDeployment());
				if(deployment == null) return;

				InstanceState state = InstanceState.valueOf(stateString);
				instance.setState(state);

				// Delete pod when instance is stopping or stopped
				if(state == InstanceState.STOPPING || state == InstanceState.STOPPED) {
					if(instance instanceof MinecraftInstance minecraftInstance) {
						turnOffPod(minecraftInstance);
					}
				}

				RedisManager.get().updateInstance(instance);
				deployment.fetchInstances();
			}
		}, RedisChannel.INSTANCE_STATE_CHANGE.getRef())).start();

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				List<Game> games = deploymentManager.getGames();
				List<Game> initialGames = games.stream()
						.filter(game -> game.isInitial() && !game.getInstances().isEmpty())
						.toList();

				if (initialGames.isEmpty()) return;

				Game game = initialGames.get((int) (Math.random() * initialGames.size()));
				MinecraftInstance instance = QueueManager.findInstance(game);

				if (instance != null) {
					RedisManager.get().publish(RedisChannel.INITIAL_INSTANCE_RESPONSE.getRef(), message + ":" + instance.getName());
				}
			}
		}, RedisChannel.REQUEST_INITIAL_INSTANCE.getRef())).start();

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");
				UUID playerId = UUID.fromString(parts[0]);
				String deploymentString = parts[1];

				Game game = deploymentManager.getGame(deploymentString);
				if(game == null) {
					//Used to send back an error to the proxy
					QueueManager.sendPlayerToInstance(playerId, null);
					return;
				}

				QueueManager.queuePlayer(playerId, game);
			}
		}, RedisChannel.QUEUE_PLAYER.getRef())).start();

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");
				UUID playerId = UUID.fromString(parts[0]);
				String ip = parts[1];

				Instance instance = BMCManager.instanceManager.getFromIP(ip);
				if(!(instance instanceof MinecraftInstance)) {
					QueueManager.sendPlayerToInstance(playerId, null);
					return;
				}

				QueueManager.sendPlayerToInstance(playerId, (MinecraftInstance) instance);
			}
		}, RedisChannel.TRANSFER_PLAYER.getRef())).start();
	}

	private void turnOffPod(MinecraftInstance instance) {
		try {
			BMCManager.kubernetesClient.pods()
				.inNamespace("default")
				.withName(instance.getPodName())
				.delete();
			System.out.println("Deleted pod: " + instance.getPodName());
		} catch (Exception e) {
			System.err.println("Failed to delete pod " + instance.getPodName() + ": " + e.getMessage());
		}
	}
}
