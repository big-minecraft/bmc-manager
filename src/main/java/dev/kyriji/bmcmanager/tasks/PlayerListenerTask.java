package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.UUID;

public class PlayerListenerTask {
	Gson gson = new Gson();

	public PlayerListenerTask() {
		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");

					UUID playerId = UUID.fromString(parts[0]);
					String username = parts[1];
					String proxyIP = parts[2];

					System.out.println(proxyIP);
					UUID proxyUid = BMCManager.networkManager.getProxyUUID(proxyIP);
					System.out.println(proxyUid);

					if(proxyUid == null) return;

					String proxyString = RedisManager.get().hgetAll("proxies").get(proxyUid.toString());
					MinecraftInstance proxy = gson.fromJson(proxyString, MinecraftInstance.class);
					if(proxy == null) return;

					proxy.addPlayer(playerId, username);
					RedisManager.get().hset("proxies", proxyUid.toString(), gson.toJson(proxy));
				}
			}, "proxy-connect");
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");

					UUID playerId = UUID.fromString(parts[0]);
					String username = parts[1];
					String proxyIP = parts[2];

					System.out.println(proxyIP);
					UUID proxyUid = BMCManager.networkManager.getProxyUUID(proxyIP);
					System.out.println(proxyUid);
					if(proxyUid == null) return;

					String proxyString = RedisManager.get().hgetAll("proxies").get(proxyUid.toString());
					MinecraftInstance proxy = gson.fromJson(proxyString, MinecraftInstance.class);
					if(proxy == null) return;

					proxy.removePlayer(playerId);
					RedisManager.get().hset("proxies", proxyUid.toString(), gson.toJson(proxy));

					removePlayerFromInstance(playerId);
				}
			}, "proxy-disconnect");
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");

					UUID playerId = UUID.fromString(parts[0]);
					String name = parts[1];
					String serverName = parts[2];

					removePlayerFromInstance(playerId);


					UUID serverUid = BMCManager.networkManager.getInstanceUUID(serverName);

					if(serverUid == null) return;

					String serverString = RedisManager.get().hgetAll("instances").get(serverUid.toString());

					MinecraftInstance server = gson.fromJson(serverString, MinecraftInstance.class);

					server.addPlayer(playerId, name);
					RedisManager.get().hset("instances", serverUid.toString(), gson.toJson(server));
				}
			}, "server-switch");
		}).start();
	}

	public void removePlayerFromInstance(UUID player) {
		List<MinecraftInstance> instances = BMCManager.networkManager.getInstances();
		for (MinecraftInstance instance : instances) {
			if (instance.hasPlayer(player)) {
				instance.removePlayer(player);
				RedisManager.get().hset("instances", instance.getUid(), gson.toJson(instance));
			}
		}
	}
}
