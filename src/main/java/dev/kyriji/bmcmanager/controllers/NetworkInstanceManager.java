package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkInstanceManager {
	private final RedisManager redisManager;
	private final ConcurrentHashMap<UUID, String> playerToProxy = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, String> playerToServer = new ConcurrentHashMap<>();
	private final Gson gson = new Gson();

	public NetworkInstanceManager(RedisManager redisManager) {
		this.redisManager = redisManager;
		clearExistingData();
		setupInitialServerListener();
	}

	private void clearExistingData() {
		redisManager.del("instances");
		redisManager.del("proxies");
		redisManager.del("player-connections");
		redisManager.publish("instance-changed", "");
	}

	public void registerProxy(MinecraftInstance proxy) {
		redisManager.hset("proxies", proxy.getUid(), gson.toJson(proxy));
	}

	public void registerInstance(MinecraftInstance instance) {
		redisManager.hset("instances", instance.getUid(), gson.toJson(instance));
		redisManager.publish("instance-changed", gson.toJson(instance));
	}

	public void unregisterInstance(String uid) {
		redisManager.hdel("instances", uid);
		redisManager.hdel("proxies", uid);
		redisManager.publish("instance-changed", "");
	}

	public void updatePlayerConnection(UUID playerId, String proxyUid, String serverUid) {
		playerToProxy.put(playerId, proxyUid);
		playerToServer.put(playerId, serverUid);
		redisManager.hset("player-connections", playerId.toString(), gson.toJson(new PlayerConnection(proxyUid, serverUid)));
	}

	public PlayerConnection getPlayerConnection(UUID playerId) {
		String proxyUid = playerToProxy.get(playerId);
		String serverUid = playerToServer.get(playerId);
		return new PlayerConnection(proxyUid, serverUid);
	}

	public List<MinecraftInstance> getInstances() {
		List<MinecraftInstance> instances = new ArrayList<>();
		redisManager.hgetAll("instances").forEach((uid, json) -> {
			MinecraftInstance instance = gson.fromJson(json, MinecraftInstance.class);
			instances.add(instance);
		});
		return instances;
	}

	private void setupInitialServerListener() {
		new Thread(() -> {
			redisManager.subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					List<MinecraftInstance> instances = getInstances();
					instances = instances.stream().filter(MinecraftInstance::isInitialServer).toList();

					if (!instances.isEmpty()) {
						MinecraftInstance instance = instances.get((int) (Math.random() * instances.size()));
						redisManager.publish("initial-server-response", message + " " + instance.getName());
					}
				}
			}, "request-initial-server");
		}).start();
	}

	public record PlayerConnection(String proxyUid, String serverUid) { }
}