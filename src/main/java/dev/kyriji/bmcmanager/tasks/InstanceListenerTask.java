package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameServerManager;
import dev.kyriji.bmcmanager.controllers.QueueManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
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
		GameServerManager gameServerManager = BMCManager.gameServerManager;

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");

				String instanceIP = parts[0];
				String stateString = parts[1];

				Instance instance = BMCManager.instanceManager.getFromIP(instanceIP);
				if(instance == null) return;
				GameServerWrapper<? extends Instance> gameServer = gameServerManager.getGameServer(instance.getDeployment());
				if(gameServer == null) return;

				InstanceState state = InstanceState.valueOf(stateString);
				instance.setState(state);

				// Delete pod when instance is stopping or stopped
				if(state == InstanceState.STOPPING || state == InstanceState.STOPPED) {
					if(instance instanceof MinecraftInstance minecraftInstance) {
						turnOffPod(minecraftInstance);
					}
				}

				RedisManager.get().updateInstance(instance);
				gameServer.fetchInstances();
			}
		}, RedisChannel.INSTANCE_STATE_CHANGE.getRef())).start();

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				List<Game> games = gameServerManager.getGames();
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

				Game game = gameServerManager.getGame(deploymentString);
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
			// With direct pod ownership (no Deployments), we just delete the pod directly
			// No need to decrement replica counts since we own pods directly via GameServer CRD
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
