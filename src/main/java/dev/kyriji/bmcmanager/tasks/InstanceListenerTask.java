package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameManager;
import dev.kyriji.bmcmanager.controllers.QueueManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.Game;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.UUID;

public class InstanceListenerTask {
	Gson gson = new Gson();

	public InstanceListenerTask() {
		GameManager gameManager = BMCManager.gameManager;

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");

					String instanceIP = parts[0];
					String stateString = parts[1];

					UUID instanceUid = BMCManager.networkManager.getInstanceUUID(instanceIP);
					if(instanceUid == null) return;

					InstanceState state = InstanceState.valueOf(stateString);

					String instanceString = RedisManager.get().hgetAll("instances").get(instanceUid.toString());
					MinecraftInstance instance = gson.fromJson(instanceString, MinecraftInstance.class);

					if(instance == null) return;
					instance.setState(state);

					if(state == InstanceState.STOPPING || state == InstanceState.STOPPED) {
						BMCManager.scalingManager.turnOffPod(instance);
					}

					RedisManager.get().hset("instances", instanceUid.toString(), gson.toJson(instance));
					Game game = gameManager.getGame(instance.getDeployment());
					if(game == null) return;

					game.fetchInstances();
				}
			}, RedisChannel.INSTANCE_STATE_CHANGE.getRef());
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					List<Game> games = gameManager.getGames();
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
			}, RedisChannel.REQUEST_INITIAL_INSTANCE.getRef());
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");
					UUID playerId = UUID.fromString(parts[0]);
					String deploymentString = parts[1];

					Game game = gameManager.getGame(deploymentString);
					if(game == null) {
						//Used to send back an error to the proxy
						QueueManager.sendPlayerToInstance(playerId, null);
						return;
					}

					QueueManager.queuePlayer(playerId, game);
				}
			}, RedisChannel.QUEUE_PLAYER.getRef());
		}).start();
	}
}
