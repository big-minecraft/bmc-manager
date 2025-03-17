package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RedisManager {
	private static RedisManager instance;
	private final JedisPool jedisPool;

	private RedisManager(String redisHost, int redisPort, int maxConnections) {
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(maxConnections);
		poolConfig.setMaxIdle(maxConnections / 4);
		poolConfig.setMinIdle(1);
		poolConfig.setMaxWait(Duration.ofSeconds(30));

		this.jedisPool = new JedisPool(poolConfig, redisHost, redisPort);
		testConnection();
	}

	public static void init(String redisHost, int redisPort) {
		synchronized (RedisManager.class) {
			if (instance != null) return;
			instance = new RedisManager(redisHost, redisPort, 25);
		}
	}

	public static RedisManager get() {
		if (instance == null) throw new IllegalStateException("RedisManager has not been initialized");
		return instance;
	}

	private void testConnection() {
		withRedis(jedis -> {
			String pong = jedis.ping();
			if (!"PONG".equals(pong)) {
				System.out.println("Failed to connect to Redis");
			}
		});
	}

	public void withRedis(Consumer<Jedis> jedisConsumer) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedisConsumer.accept(jedis);
		}
	}

	public void hset(String key, String field, String value) {
		withRedis(jedis -> jedis.hset(key, field, value));
	}

	public void hdelAll(String key) {
		withRedis(jedis -> {
			Set<String> fields = jedis.hkeys(key);
			if (!fields.isEmpty()) {
				jedis.hdel(key, fields.toArray(new String[0]));
			}
		});
	}

	public void publish(String channel, String message) {
		withRedis(jedis -> jedis.publish(channel, message));
	}

	public void subscribe(JedisPubSub jedisPubSub, String... channels) {
		withRedis(jedis -> jedis.subscribe(jedisPubSub, channels));
	}

	public void clear() {
		withRedis(Jedis::flushAll);
	}

	public void updateInstance(Instance instance) {
		String key = "instance:" + instance.getUid() + ":" + instance.getDeployment();
		hset(key, "uid", instance.getUid());
		hset(key, "name", instance.getName());
		hset(key, "podName", instance.getPodName());
		hset(key, "ip", instance.getIp());
		hset(key, "state", instance.getState().name());
	}

	public List<Instance> scanAndDeserializeInstances(String pattern, Type typeClass) {
		Gson gson = new Gson();
		List<Instance> resultList = new ArrayList<>();
		Type playerMapType = new TypeToken<Map<UUID, String>>(){}.getType();

		try(Jedis jedis = jedisPool.getResource()) {
			String cursor = "0";
			do {
				ScanResult<String> scanResult = jedis.scan(cursor, new ScanParams().match(pattern).count(100));
				cursor = scanResult.getCursor();

				for(String key : scanResult.getResult()) {
					Map<String, String> hashData = jedis.hgetAll(key);

					String uid = hashData.get("uid");
					String name = hashData.get("name");
					String podName = hashData.get("podName");
					String ip = hashData.get("ip");
					String deployment = hashData.get("deployment");
					String stateStr = hashData.get("state");

					InstanceState state = stateStr != null ? InstanceState.valueOf(stateStr) : null;

					Instance instance;
					if(typeClass instanceof MinecraftInstance) {
						String playersStr = hashData.get("players");
						Map<UUID, String> players = playersStr != null ?
								gson.fromJson(playersStr, playerMapType) : new HashMap<>();

						instance = new MinecraftInstance(uid, name, podName, ip, deployment);
						((MinecraftInstance) instance).setPlayers(players);
					} else {
						instance = new Instance(uid, name, podName, ip, deployment);
					}

					instance.setState(state);
					resultList.add(instance);
				}
			} while (!cursor.equals("0"));
		}

		return resultList;
	}
}