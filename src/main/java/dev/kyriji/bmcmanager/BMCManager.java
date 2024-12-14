package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.DeploymentManager;
import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.tasks.DeploymentDiscoveryTask;
import dev.kyriji.bmcmanager.tasks.InstanceListenerTask;
import dev.kyriji.bmcmanager.tasks.PlayerListenerTask;
import dev.kyriji.bmcmanager.tasks.ServerDiscoveryTask;
import dev.kyriji.bmcmanager.controllers.RedisManager;

public class BMCManager {
	public static DeploymentManager deploymentManager;
	public static ServerDiscoveryTask serverDiscovery;
	public static NetworkInstanceManager networkManager;
	public static PlayerListenerTask playerListener;
	public static DeploymentDiscoveryTask deploymentDiscovery;
	public static InstanceListenerTask instanceListener;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		deploymentManager = new DeploymentManager();
		networkManager = new NetworkInstanceManager();
		serverDiscovery = new ServerDiscoveryTask(networkManager);
		playerListener = new PlayerListenerTask();
		deploymentDiscovery = new DeploymentDiscoveryTask();
		instanceListener = new InstanceListenerTask();
	}
}
