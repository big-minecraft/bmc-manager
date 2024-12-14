package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.*;
import dev.kyriji.bmcmanager.tasks.*;

public class BMCManager {
	public static GamemodeManager gamemodeManager;
	public static ProxyManager proxyManager;
	public static ServerDiscoveryTask serverDiscovery;
	public static NetworkInstanceManager networkManager;
	public static PlayerListenerTask playerListener;
	public static GamemodeDiscoveryTask gamemodeDiscovery;
	public static ProxyDiscoveryTask proxyDiscovery;
	public static InstanceListenerTask instanceListener;
	public static ScalingManager scalingManager;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		gamemodeManager = new GamemodeManager();
		proxyManager = new ProxyManager();
		networkManager = new NetworkInstanceManager();
		serverDiscovery = new ServerDiscoveryTask(networkManager);
		playerListener = new PlayerListenerTask();
		gamemodeDiscovery = new GamemodeDiscoveryTask();
		proxyDiscovery = new ProxyDiscoveryTask();
		instanceListener = new InstanceListenerTask();
		scalingManager = new ScalingManager();
	}
}
