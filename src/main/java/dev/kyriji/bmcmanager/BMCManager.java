package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.redis.RedisManager;

public class BMCManager {

	public static void main(String[] args) {
		RedisManager.init();
	}
}
