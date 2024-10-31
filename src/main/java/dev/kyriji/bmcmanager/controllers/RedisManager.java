package dev.kyriji.bmcmanager.controllers;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;

public class RedisManager {
	private static RedisManager instance;
	private final JedisPool jedisPool;

	private RedisManager(String redisHost, int redisPort) {
		this.jedisPool = new JedisPool(redisHost, redisPort);
		testConnection();
	}

	public static void init(String redisHost, int redisPort) {
		synchronized (RedisManager.class) {
			if (instance != null) return;
			instance = new RedisManager(redisHost, redisPort);
		}
	}

	public static RedisManager get() {
		if (instance == null) throw new IllegalStateException("RedisManager has not been initialized");
		return instance;
	}

	private void testConnection() {
		try (Jedis jedis = jedisPool.getResource()) {
			String pong = jedis.ping();
			if (!"PONG".equals(pong)) {
				System.out.println("Failed to connect to Redis");
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("An error occurred while connecting to Redis");
		}
	}

	public void del(String key) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.del(key);
		}
	}

	public void hset(String key, String field, String value) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.hset(key, field, value);
		}
	}

	public void hdel(String key, String field) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.hdel(key, field);
		}
	}

	public Map<String, String> hgetAll(String key) {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.hgetAll(key);
		}
	}

	public void publish(String channel, String message) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.publish(channel, message);
		}
	}

	public void subscribe(JedisPubSub jedisPubSub, String... channels) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.subscribe(jedisPubSub, channels);
		}
	}
}