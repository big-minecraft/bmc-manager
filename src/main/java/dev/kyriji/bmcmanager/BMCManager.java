package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controller.InformerManager;
import dev.kyriji.bmcmanager.controller.ReconciliationQueue;
import dev.kyriji.bmcmanager.controllers.*;
import dev.kyriji.bmcmanager.tasks.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.Map;

public class BMCManager {
	public static DeploymentManager deploymentManager;
	public static InstanceDiscoveryTask serverDiscovery;
	public static InstanceManager instanceManager;
	public static PlayerListenerTask playerListener;
	public static DeploymentDiscoveryTask gameDiscovery;
	public static InstanceListenerTask instanceListener;
	public static InformerManager informerManager;
	public static KubernetesClient kubernetesClient;

	public static void main(String[] args) {
		System.out.println("=== Starting BMC Manager ===");

		// Initialize Redis
		RedisManager.init(getRedisHost(), getRedisPort());

		// Initialize Kubernetes client
		kubernetesClient = new KubernetesClientBuilder().build();

		// Initialize existing managers
		deploymentManager = new DeploymentManager();
		instanceManager = new InstanceManager();

		// NEW: Setup event-driven controller with informers
		System.out.println("Setting up event-driven controller...");
		ReconciliationQueue queue = new ReconciliationQueue();
		informerManager = new InformerManager(kubernetesClient, queue);
		informerManager.setupInformers();
		informerManager.start();

		// Keep existing tasks
		serverDiscovery = new InstanceDiscoveryTask(instanceManager);
		playerListener = new PlayerListenerTask();
		gameDiscovery = new DeploymentDiscoveryTask();
		instanceListener = new InstanceListenerTask();

		System.out.println("=== BMC Manager started successfully ===");
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
