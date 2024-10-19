package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.k8s.ServerDiscovery;
import dev.kyriji.bmcmanager.redis.RedisManager;

public class BMCManager {
	public static ServerDiscovery serverDiscovery;

	public static void main(String[] args) {
		RedisManager.init();
		serverDiscovery = new ServerDiscovery();
	}
}
