package dev.kyriji.bmcmanager;

import dev.kyriji.bmcmanager.controller.InformerManager;
import dev.kyriji.bmcmanager.controller.ReconciliationQueue;
import dev.kyriji.bmcmanager.controllers.*;
import dev.kyriji.bmcmanager.tasks.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.Map;

public class BMCManager {
	public static GameServerManager gameServerManager;
	public static InstanceDiscoveryTask serverDiscovery;
	public static InstanceManager instanceManager;
	public static PlayerListenerTask playerListener;
	public static GameServerDiscoveryTask gameServerDiscovery;
	public static InstanceListenerTask instanceListener;
	public static DeploymentToggleListenerTask deploymentToggleListener;
	public static ShutdownResponseListenerTask shutdownResponseListener;
	public static ShutdownTimeoutCheckerTask shutdownTimeoutChecker;
	public static InstanceAgeCheckerTask instanceAgeChecker;
	public static InformerManager informerManager;
	public static KubernetesClient kubernetesClient;

	public static void main(String[] args) {
		System.out.println("=== Starting BMC Manager ===");

		// Initialize Redis
		RedisManager.init(getRedisHost(), getRedisPort());

		// Initialize Kubernetes client
		kubernetesClient = new KubernetesClientBuilder().build();

		// Initialize managers
		gameServerManager = new GameServerManager();
		instanceManager = new InstanceManager();

		// Setup event-driven controller with informers for GameServer CRDs
		System.out.println("Setting up event-driven controller for GameServer CRDs...");
		ReconciliationQueue queue = new ReconciliationQueue();
		informerManager = new InformerManager(kubernetesClient, queue);
		informerManager.setupInformers();
		informerManager.start();

		// Initialize tasks - GameServer discovery must run FIRST (synchronously)
		// so wrappers exist before instances are discovered
		gameServerDiscovery = new GameServerDiscoveryTask();
		gameServerDiscovery.discoverGameServers();

		serverDiscovery = new InstanceDiscoveryTask(instanceManager);
		playerListener = new PlayerListenerTask();
		instanceListener = new InstanceListenerTask();
		deploymentToggleListener = new DeploymentToggleListenerTask();

		// Shutdown negotiation tasks
		shutdownResponseListener = new ShutdownResponseListenerTask();
		shutdownTimeoutChecker = new ShutdownTimeoutCheckerTask();
		instanceAgeChecker = new InstanceAgeCheckerTask();

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
