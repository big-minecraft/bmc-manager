package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.*;
import dev.kyriji.bmcmanager.tasks.*;

public class BMCManager {
	public static DeploymentManager deploymentManager;
	public static ProxyManager proxyManager;
	public static ServerDiscoveryTask serverDiscovery;
	public static NetworkInstanceManager networkManager;
	public static PlayerListenerTask playerListener;
	public static DeploymentDiscoveryTask deploymentDiscovery;
	public static ProxyDiscoveryTask proxyDiscovery;
	public static InstanceListenerTask instanceListener;
	public static ScalingManager scalingManager;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		deploymentManager = new DeploymentManager();
		proxyManager = new ProxyManager();
		networkManager = new NetworkInstanceManager();
		serverDiscovery = new ServerDiscoveryTask(networkManager);
		playerListener = new PlayerListenerTask();
		deploymentDiscovery = new DeploymentDiscoveryTask();
		proxyDiscovery = new ProxyDiscoveryTask();
		instanceListener = new InstanceListenerTask();
		scalingManager = new ScalingManager();
	}
}
