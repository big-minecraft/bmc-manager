package dev.kyriji.bmcmanager.redis;

import com.google.gson.Gson;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;

public class RedisManager {
	private static JedisPool jedisPool;
	private static Gson gson;

	public static String INITIAL_SERVER_TAG = "kyriji.dev/initial-server";

	public static void init() {
		String redisHost = "redis-service";
		int redisPort = 6379;

		gson = new Gson();
		jedisPool = new JedisPool(redisHost, redisPort);

		try (Jedis jedis = jedisPool.getResource()) {
			String pong = jedis.ping();
			if ("PONG".equals(pong)) {
				System.out.println("Connected to Redis successfully.");
				jedis.del("instances");
				jedis.del("proxies");

				try (Jedis jedisPub = jedisPool.getResource()) {
					jedisPub.publish("instance-changed", "");
				}
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
						instances = instances.stream().filter(MinecraftInstance::isInitialServer).toList();

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
		String podName = pod.getMetadata().getName();
		String uid = pod.getMetadata().getUid();
		String ip = pod.getStatus().getPodIP();
		String gamemode = pod.getMetadata().getLabels().get("app");

		String initialServerTag = pod.getMetadata().getLabels().get(INITIAL_SERVER_TAG);
		boolean initialServer = initialServerTag != null && initialServerTag.equals("true");

			MinecraftInstance instance = new MinecraftInstance(uid, name, podName, ip, gamemode, initialServer);

			String json = gson.toJson(instance);

			try (Jedis jedisPub = jedisPool.getResource()) {
				jedisPub.hset("instances", uid, json);
			}

		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.publish("instance-changed", json);
		}
	}

	public static void registerProxy(Pod pod) {
		String name = generateName(pod);
		String podName = pod.getMetadata().getName();
		String uid = pod.getMetadata().getUid();
		String ip = pod.getStatus().getPodIP();
		String gamemode = pod.getMetadata().getLabels().get("app");

		String initialServerTag = pod.getMetadata().getLabels().get(INITIAL_SERVER_TAG);
		boolean initialServer = initialServerTag != null && initialServerTag.equals("true");

		MinecraftInstance proxy = new MinecraftInstance(uid, name, podName, ip, gamemode, initialServer);

		String json = gson.toJson(proxy);

		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.hset("proxies", uid, json);
		}
	}

	public static void unregisterPod(String uid) {
		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.hdel("instances", uid);
		}

		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.hdel("proxies", uid);
		}

		String name = getRegisteredName(uid);
		if (name == null) return;

		try (Jedis jedisPub = jedisPool.getResource()) {
			jedisPub.publish("instance-changed", name);
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
