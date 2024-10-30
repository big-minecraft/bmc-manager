package dev.kyriji.bmcmanager.controllers;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.Map;

public class RedisManager {
	private final JedisPool jedisPool;

	public RedisManager(String redisHost, int redisPort) {
		this.jedisPool = new JedisPool(redisHost, redisPort);
		testConnection();
	}

	private void testConnection() {
		System.out.println("testing connection");
		try (Jedis jedis = jedisPool.getResource()) {
			System.out.println("pinging");
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