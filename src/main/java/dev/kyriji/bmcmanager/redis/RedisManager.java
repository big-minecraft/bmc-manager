package dev.kyriji.bmcmanager.redis;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RedisManager {
	public static Jedis jedis;
	public static Gson gson;

	public static void init() {
		String redisHost = "redis-service";
		int redisPort = 6379;

		gson = new Gson();

		try {
			jedis = new Jedis(redisHost, redisPort);
			String pong = jedis.ping();
			if ("PONG".equals(pong)) {
				System.out.println("Connected to Redis successfully.");
			} else {
				System.out.println("Failed to connect to Redis.");
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("An error occurred while connecting to Redis.");
		}
	}

	public static void registerInstance(Pod pod) {
		String name = generateName(pod);
		String uid = pod.getMetadata().getUid();
		String ip = pod.getStatus().getPodIP();

		MinecraftInstance instance = new MinecraftInstance(uid, name, ip);

		String json = gson.toJson(instance);

		jedis.hset("instances", uid, json);

		//TODO: Send message to proxies to fetch
	}

	public static void unregisterInstance(String uid) {
		String name = getRegisteredName(uid);
		if (name == null) return;

		jedis.hdel("instances", uid);
	}

	public static String getRegisteredName(String uid) {
		String json = jedis.hget("instances", uid);
		if (json == null) return null;

		MinecraftInstance instance = gson.fromJson(json, MinecraftInstance.class);

		return instance.getName();
	}

	public static String generateName(Pod pod) {
		String deploymentName = pod.getMetadata().getLabels().get("app");

		String uid = pod.getMetadata().getUid();

		return deploymentName + "-" + uid.substring(0, 5);
	}
}
