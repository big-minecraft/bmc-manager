package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.tasks.GamemodeDiscoveryTask;
import dev.kyriji.bmcmanager.tasks.PlayerListenerTask;
import dev.kyriji.bmcmanager.tasks.ServerDiscoveryTask;
import dev.kyriji.bmcmanager.controllers.RedisManager;

public class BMCManager {
	public static ServerDiscoveryTask serverDiscovery;
	public static NetworkInstanceManager networkManager;
	public static PlayerListenerTask playerListener;
	public static GamemodeDiscoveryTask gamemodeDiscovery;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		networkManager = new NetworkInstanceManager();
		serverDiscovery = new ServerDiscoveryTask(networkManager);
		playerListener = new PlayerListenerTask();
		gamemodeDiscovery = new GamemodeDiscoveryTask();

// 		To track a player connection
//		UUID playerId = player.getUuid();
//		String proxyUid = "proxy-123";
//		String serverUid = "server-456";
//		networkManager.updatePlayerConnection(playerId, proxyUid, serverUid);

// 		To get a player's current connection
//		PlayerConnection connection = networkManager.getPlayerConnection(playerId);
	}
}
