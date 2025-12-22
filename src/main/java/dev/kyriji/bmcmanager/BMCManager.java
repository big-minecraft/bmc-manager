package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controllers.*;
import dev.kyriji.bmcmanager.tasks.*;

import java.util.Map;

public class BMCManager {
	public static DeploymentManager deploymentManager;
	public static InstanceDiscoveryTask serverDiscovery;
	public static InstanceManager instanceManager;
	public static PlayerListenerTask playerListener;
	public static DeploymentDiscoveryTask gameDiscovery;
	public static InstanceListenerTask instanceListener;
	public static ScalingManager scalingManager;

	public static void main(String[] args) {
		RedisManager.init(getRedisHost(), getRedisPort());
		deploymentManager = new DeploymentManager();
		instanceManager = new InstanceManager();
		serverDiscovery = new InstanceDiscoveryTask(instanceManager);
		playerListener = new PlayerListenerTask();
		gameDiscovery = new DeploymentDiscoveryTask();
		instanceListener = new InstanceListenerTask();
		scalingManager = new ScalingManager();
	}

	public static String getRedisHost() {
		Map<String, String> env = System.getenv();
		return env.getOrDefault("REDIS_HOST", "redis-service");
	}

	public static int getRedisPort() {
		Map<String, String> env = System.getenv();
			String portStr = env.getOrDefault("REDIS_PORT", "6379");
		return Integer.parseInt(portStr);
	}
}
