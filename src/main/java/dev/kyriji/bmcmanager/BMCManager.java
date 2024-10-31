package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.tasks.ServerDiscoveryTask;
import dev.kyriji.bmcmanager.controllers.RedisManager;

public class BMCManager {
	public static ServerDiscoveryTask serverDiscoveryTask;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		NetworkInstanceManager networkManager = new NetworkInstanceManager();
		ServerDiscoveryTask serverDiscovery = new ServerDiscoveryTask(networkManager);

// 		To track a player connection
//		UUID playerId = player.getUuid();
//		String proxyUid = "proxy-123";
//		String serverUid = "server-456";
//		networkManager.updatePlayerConnection(playerId, proxyUid, serverUid);

// 		To get a player's current connection
//		PlayerConnection connection = networkManager.getPlayerConnection(playerId);
	}
}
