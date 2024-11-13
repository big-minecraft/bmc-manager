package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.GamemodeManager;
import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.controllers.ScalingManager;
import dev.kyriji.bmcmanager.tasks.GamemodeDiscoveryTask;
import dev.kyriji.bmcmanager.tasks.InstanceListenerTask;
import dev.kyriji.bmcmanager.tasks.PlayerListenerTask;
import dev.kyriji.bmcmanager.tasks.ServerDiscoveryTask;
import dev.kyriji.bmcmanager.controllers.RedisManager;

public class BMCManager {
	public static GamemodeManager gamemodeManager;
	public static ServerDiscoveryTask serverDiscovery;
	public static NetworkInstanceManager networkManager;
	public static PlayerListenerTask playerListener;
	public static GamemodeDiscoveryTask gamemodeDiscovery;
	public static InstanceListenerTask instanceListener;
	public static ScalingManager scalingManager;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		gamemodeManager = new GamemodeManager();
		networkManager = new NetworkInstanceManager();
		serverDiscovery = new ServerDiscoveryTask(networkManager);
		playerListener = new PlayerListenerTask();
		gamemodeDiscovery = new GamemodeDiscoveryTask();
		instanceListener = new InstanceListenerTask();
		scalingManager = new ScalingManager();
	}
}
