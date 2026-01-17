package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameServerManager;
import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.*;

public class GameServerDiscoveryTask {
	private final KubernetesClient client;

	public GameServerDiscoveryTask() {
		this.client = new KubernetesClientBuilder().build();
		// GameServer CRD changes are watched by InformerManager
		// Initial discovery is called synchronously from BMCManager before instance discovery
	}

	public void discoverGameServers() {
		GameServerManager gameServerManager = BMCManager.gameServerManager;

		List<GameServerWrapper<?>> existingGameServers = gameServerManager.getGameServers();
		List<GameServerWrapper<?>> newGameServers = new ArrayList<>();

		// List all GameServer CRDs in the default namespace
		List<GameServer> gameServers = client.resources(GameServer.class)
				.inNamespace("default")
				.list()
				.getItems();

		for (GameServer gameServer : gameServers) {
			GameServerWrapper<?> wrapper = gameServerManager.createWrapper(gameServer);
			if (wrapper == null) continue;

			if (existingGameServers.contains(wrapper)) {
				existingGameServers.remove(wrapper);
				gameServerManager.updateGameServer(wrapper);
			} else {
				newGameServers.add(wrapper);
			}
		}

		// Unregister removed GameServers
		for (GameServerWrapper<?> existingGameServer : existingGameServers) {
			gameServerManager.unregisterGameServer(existingGameServer);
		}

		// Register new GameServers
		for (GameServerWrapper<?> newGameServer : newGameServers) {
			gameServerManager.registerGameServer(newGameServer);
		}

		// Fetch instances for all GameServers
		for (GameServerWrapper<?> gameServer : gameServerManager.getGameServers()) {
			gameServer.fetchInstances();
		}
	}
}
