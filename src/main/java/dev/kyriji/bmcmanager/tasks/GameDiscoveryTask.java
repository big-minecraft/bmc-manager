package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.objects.Game;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import redis.clients.jedis.JedisPubSub;

import java.util.*;

public class GameDiscoveryTask {
	private final KubernetesClient client;

	public GameDiscoveryTask() {
		this.client = new KubernetesClientBuilder().build();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					discoverGames();
				}
			}, RedisChannel.DEPLOYMENT_MODIFIED.getRef());
		}).start();

		new Thread(() -> {
			//Sleep to ensure that ServerDiscoveryTask has had time to register all instances
			try { Thread.sleep(5000); } catch(InterruptedException e) { throw new RuntimeException(e); }
			discoverGames();
		}).start();
	}

	public void discoverGames() {
		GameManager gameManager = BMCManager.gameManager;

		List<Game> existingGames = gameManager.getGames();
		List<Game> newGames = new ArrayList<>();

		List<Deployment> gameDeployments = client.apps().deployments()
				.inNamespace("default")
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

		gameDeployments.forEach(deployment -> {
			Game game = new Game(deployment);
			if(existingGames.contains(game)) {
				existingGames.remove(game);
			} else {
				newGames.add(game);
			}
		});

		for(Game existingGame : existingGames) gameManager.unregisterGame(existingGame);
		for(Game newGame : newGames) gameManager.registerGame(newGame);
		for(Game game : gameManager.getGames()) game.fetchInstances();
	}
}