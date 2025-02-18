package dev.kyriji.bmcmanager.controllers;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.time.Duration;
import java.util.Map;
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

	public void del(String key) {
		withRedis(jedis -> jedis.del(key));
	}

	public void hset(String key, String field, String value) {
		withRedis(jedis -> jedis.hset(key, field, value));
	}

	public void hdel(String key, String field) {
		withRedis(jedis -> jedis.hdel(key, field));
	}

	public Map<String, String> hgetAll(String key) {
		//TODO: dd
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.hgetAll(key);
		}
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
}