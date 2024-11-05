package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkInstanceManager {
	private final ConcurrentHashMap<UUID, String> playerToProxy = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, String> playerToServer = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, UUID> instanceNameToUUID = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, UUID> proxyIpToUUID = new ConcurrentHashMap<>();
	private final Gson gson = new Gson();

	public NetworkInstanceManager() {
		clearExistingData();
		setupInitialServerListener();
	}

	private void clearExistingData() {
		RedisManager.get().del("instances");
		RedisManager.get().del("proxies");
		RedisManager.get().del("player-connections");
		RedisManager.get().publish("instance-changed", "");
	}

	public void registerProxy(MinecraftInstance proxy) {
		proxyIpToUUID.put(proxy.getIp(), UUID.fromString(proxy.getUid()));
		RedisManager.get().hset("proxies", proxy.getUid(), gson.toJson(proxy));
		RedisManager.get().publish("proxy-registered", gson.toJson(proxy));
	}

	public void registerInstance(MinecraftInstance instance) {
		instanceNameToUUID.put(instance.getName(), UUID.fromString(instance.getUid()));
		RedisManager.get().hset("instances", instance.getUid(), gson.toJson(instance));
		RedisManager.get().publish("instance-changed", gson.toJson(instance));
	}

	public void unregisterInstance(String uid) {
		MinecraftInstance instance = gson.fromJson(RedisManager.get().hgetAll("instances").get(uid), MinecraftInstance.class);
		if (instance != null) instanceNameToUUID.remove(instance.getName());

		MinecraftInstance proxy = gson.fromJson(RedisManager.get().hgetAll("proxies").get(uid), MinecraftInstance.class);
		if (proxy != null) proxyIpToUUID.remove(proxy.getIp());

		RedisManager.get().hdel("instances", uid);
		RedisManager.get().hdel("proxies", uid);
		RedisManager.get().publish("instance-changed", "");
	}

	public void updatePlayerConnection(UUID playerId, String proxyUid, String serverUid) {
		playerToProxy.put(playerId, proxyUid);
		playerToServer.put(playerId, serverUid);
		RedisManager.get().hset("player-connections", playerId.toString(), gson.toJson(new PlayerConnection(proxyUid, serverUid)));
	}

	public PlayerConnection getPlayerConnection(UUID playerId) {
		String proxyUid = playerToProxy.get(playerId);
		String serverUid = playerToServer.get(playerId);
		return new PlayerConnection(proxyUid, serverUid);
	}

	public UUID getInstanceUUID(String name) {
		return instanceNameToUUID.get(name);
	}

	public UUID getProxyUUID(String ip) {
		return proxyIpToUUID.get(ip);
	}

	public List<MinecraftInstance> getInstances() {
		List<MinecraftInstance> instances = new ArrayList<>();
		RedisManager.get().hgetAll("instances").forEach((uid, json) -> {
			MinecraftInstance instance = gson.fromJson(json, MinecraftInstance.class);
			instances.add(instance);
		});
		return instances;
	}

	private void setupInitialServerListener() {
		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					List<MinecraftInstance> instances = getInstances();
					instances = instances.stream().filter(MinecraftInstance::isInitialServer).toList();

					if (!instances.isEmpty()) {
						MinecraftInstance instance = instances.get((int) (Math.random() * instances.size()));
						RedisManager.get().publish("initial-server-response", message + " " + instance.getName());
					}
				}
			}, "request-initial-server");
		}).start();
	}

	public record PlayerConnection(String proxyUid, String serverUid) { }
}