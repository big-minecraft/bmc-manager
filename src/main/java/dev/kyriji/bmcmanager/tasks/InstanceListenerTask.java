package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GamemodeManager;
import dev.kyriji.bmcmanager.controllers.QueueManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.Gamemode;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.UUID;

public class InstanceListenerTask {
	Gson gson = new Gson();

	public InstanceListenerTask() {
		GamemodeManager gamemodeManager = BMCManager.gamemodeManager;

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

					RedisManager.get().hset("instances", instanceUid.toString(), gson.toJson(instance));
					Gamemode gamemode = gamemodeManager.getGamemode(instance.getGamemode());
					if(gamemode == null) return;

					gamemode.fetchInstances();
				}
			}, RedisChannel.INSTANCE_STATE_CHANGE.getRef());
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					List<Gamemode> gamemodes = gamemodeManager.getGamemodes();
					List<Gamemode> initialGamemodes = gamemodes.stream()
							.filter(gamemode -> gamemode.isInitial() && !gamemode.getInstances().isEmpty())
							.toList();

					if (initialGamemodes.isEmpty()) return;

					Gamemode gamemode = initialGamemodes.get((int) (Math.random() * initialGamemodes.size()));
					MinecraftInstance instance = QueueManager.findInstance(gamemode);

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
					String gamemodeString = parts[1];

					Gamemode gamemode = gamemodeManager.getGamemode(gamemodeString);
					if(gamemode == null) {
						//Used to send back an error to the proxy
						QueueManager.sendPlayerToInstance(playerId, null);
						return;
					}

					QueueManager.queuePlayer(playerId, gamemode);
				}
			}, RedisChannel.QUEUE_PLAYER.getRef());
		}).start();
	}
}
