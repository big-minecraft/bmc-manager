package dev.kyriji.bmcmanager.redis;

import com.google.gson.Gson;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;

public class RedisManager {
	private static JedisPool jedisPool;
	private static Gson gson;

	public static void init() {
		String redisHost = "redis-service";
		int redisPort = 6379;

		gson = new Gson();
		jedisPool = new JedisPool(redisHost, redisPort);

		try (Jedis jedis = jedisPool.getResource()) {
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

		new Thread(() -> {
			try (Jedis jedisSub = jedisPool.getResource()) {
				jedisSub.subscribe(new JedisPubSub() {
					@Override
					public void onMessage(String channel, String message) {
						List<MinecraftInstance> instances = getInstances();
						MinecraftInstance instance = instances.get((int) (Math.random() * instances.size()));
						try (Jedis jedisPub = jedisPool.getResource()) {
							jedisPub.publish("initial-server-response", message + " " + instance.getName());
						}
					}
				}, "request-initial-server");
			}
		}).start();
	}

	public static void registerInstance(Pod pod) {
		String name = generateName(pod);
		String uid = pod.getMetadata().getUid();
		String ip = pod.getStatus().getPodIP();

		MinecraftInstance instance = new MinecraftInstance(uid, name, ip);

		String json = gson.toJson(instance);

		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.hset("instances", uid, json);

			jedisPub.publish("instance-registered", json);
		}
	}

	public static void unregisterInstance(String uid) {
		String name = getRegisteredName(uid);
		if (name == null) return;

		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.hdel("instances", uid);
		}
	}

	public static String getRegisteredName(String uid) {
		try (Jedis jedisPub = jedisPool.getResource()) {
			String json = jedisPub.hget("instances", uid);
			if (json == null) return null;

			MinecraftInstance instance = gson.fromJson(json, MinecraftInstance.class);
			return instance.getName();
		}
	}

	public static String generateName(Pod pod) {
		String deploymentName = pod.getMetadata().getLabels().get("app");
		String uid = pod.getMetadata().getUid();
		return deploymentName + "-" + uid.substring(0, 5);
	}

	public static List<MinecraftInstance> getInstances() {
		List<MinecraftInstance> instances = new ArrayList<>();
		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.hgetAll("instances").forEach((uid, json) -> {
				MinecraftInstance instance = gson.fromJson(json, MinecraftInstance.class);
				instances.add(instance);
			});
		}
		return instances;
	}
}
