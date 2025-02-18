package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.*;
import dev.kyriji.bmcmanager.tasks.*;

public class BMCManager {
	public static DeploymentManager deploymentManager;
	public static InstanceDiscoveryTask serverDiscovery;
	public static InstanceManager instanceManager;
	public static PlayerListenerTask playerListener;
	public static DeploymentDiscoveryTask gameDiscovery;
	public static InstanceListenerTask instanceListener;
	public static ScalingManager scalingManager;

	public static void main(String[] args) {
		RedisManager.init("redis-service", 6379);
		deploymentManager = new DeploymentManager();
		instanceManager = new InstanceManager();
		serverDiscovery = new InstanceDiscoveryTask(instanceManager);
		playerListener = new PlayerListenerTask();
		gameDiscovery = new DeploymentDiscoveryTask();
		instanceListener = new InstanceListenerTask();
		scalingManager = new ScalingManager();
	}
}
