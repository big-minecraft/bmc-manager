package dev.kyriji.bmcmanager.redis;

import redis.clients.jedis.Jedis;

public class RedisManager {

	public static void init() {
		String redisHost = "15.204.86.49";
		int redisPort = 30079;

		try (Jedis jedis = new Jedis(redisHost, redisPort)) {
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
}
