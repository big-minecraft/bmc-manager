package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkInstanceManager {
	private final ConcurrentHashMap<String, UUID> instanceIpToUUID = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, UUID> proxyIpToUUID = new ConcurrentHashMap<>();
	private final Gson gson = new Gson();

	public NetworkInstanceManager() {
		clearExistingData();
	}

	private void clearExistingData() {
		RedisManager.get().del("instances");
		RedisManager.get().del("proxies");
		RedisManager.get().del("player-connections");
		RedisManager.get().publish(RedisChannel.INSTANCE_MODIFIED.getRef(), "");
	}

	public void registerProxy(MinecraftInstance proxy) {
		proxyIpToUUID.put(proxy.getIp(), UUID.fromString(proxy.getUid()));
		RedisManager.get().hset("proxies", proxy.getUid(), gson.toJson(proxy));
		RedisManager.get().publish(RedisChannel.PROXY_REGISTER.getRef(), gson.toJson(proxy));
	}

	public void registerInstance(MinecraftInstance instance) {
		instanceIpToUUID.put(instance.getIp(), UUID.fromString(instance.getUid()));
		RedisManager.get().hset("instances", instance.getUid(), gson.toJson(instance));

		RedisManager.get().publish(RedisChannel.INSTANCE_MODIFIED.getRef(), gson.toJson(instance));
	}

	public void unregisterInstance(String uid) {
		MinecraftInstance instance = gson.fromJson(RedisManager.get().hgetAll("instances").get(uid), MinecraftInstance.class);
		if (instance != null) instanceIpToUUID.remove(instance.getIp());

		MinecraftInstance proxy = gson.fromJson(RedisManager.get().hgetAll("proxies").get(uid), MinecraftInstance.class);
		if (proxy != null) proxyIpToUUID.remove(proxy.getIp());

		RedisManager.get().hdel("instances", uid);
		RedisManager.get().hdel("proxies", uid);
		RedisManager.get().publish(RedisChannel.INSTANCE_MODIFIED.getRef(), "");
	}

	public UUID getInstanceUUID(String ip) {
		return instanceIpToUUID.get(ip);
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
}