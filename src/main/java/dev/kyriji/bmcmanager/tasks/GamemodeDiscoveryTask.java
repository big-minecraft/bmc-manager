package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GamemodeManager;
import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.factories.MinecraftInstanceFactory;
import dev.kyriji.bmcmanager.objects.Gamemode;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import redis.clients.jedis.JedisPubSub;

import java.util.*;

public class GamemodeDiscoveryTask {
	private final KubernetesClient client;

	public GamemodeDiscoveryTask() {
		this.client = new KubernetesClientBuilder().build();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					discoverGamemodes();
				}
			}, "gamemode-modified");
		}).start();

		new Thread(() -> {
			//Sleep to ensure that ServerDiscoveryTask has had time to register all instances
			try { Thread.sleep(5000); } catch(InterruptedException e) { throw new RuntimeException(e); }
			discoverGamemodes();
		}).start();
	}

	public void discoverGamemodes() {
		List<Gamemode> existingGamemodes = new ArrayList<>();
		List<Gamemode> newGamemodes = new ArrayList<>();

		List<Deployment> deployments = client.apps().deployments()
				.inNamespace("minecraft")
				.list()
				.getItems()
				.stream()
				.filter(deployment ->
						deployment.getSpec() != null &&
								deployment.getSpec().getTemplate() != null &&
								deployment.getSpec().getTemplate().getMetadata() != null &&
								deployment.getSpec().getTemplate().getMetadata().getLabels() != null &&
								"true".equals(deployment.getSpec().getTemplate().getMetadata().getLabels()
										.get(DeploymentLabel.SERVER_DISCOVERY.getLabel())))
				.toList();

		deployments.forEach(deployment -> {
			Gamemode gamemode = new Gamemode(deployment);
			System.out.println("Found gamemode: " + gamemode.getName());
			if(existingGamemodes.contains(gamemode)) {
				existingGamemodes.remove(gamemode);
			} else {
				newGamemodes.add(gamemode);
			}
		});

		for(Gamemode existingGamemode : existingGamemodes) {
			GamemodeManager.unregisterGamemode(existingGamemode);
		}
		for(Gamemode newGamemode : newGamemodes) {
			GamemodeManager.registerGamemode(newGamemode);
		}

		for(Gamemode gamemode : GamemodeManager.getGamemodes()) {
			System.out.println("Fetching instances for gamemode: " + gamemode.getName());
			gamemode.fetchInstances();
		}
	}
}