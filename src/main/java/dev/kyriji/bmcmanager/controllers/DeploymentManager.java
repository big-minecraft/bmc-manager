package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.enums.DeploymentType;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.kyriji.bmcmanager.objects.Game;
import dev.kyriji.bmcmanager.objects.Process;
import dev.kyriji.bmcmanager.objects.Proxy;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.ArrayList;
import java.util.List;

public class DeploymentManager {
	public List<DeploymentWrapper<?>> deployments;

	public DeploymentManager() {
		this.deployments = new ArrayList<>();

		new Thread(() -> {
			while (true) {
				for(DeploymentWrapper<?> deployment : deployments) deployment.scale();
				try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
			}
		}).start();
	}

	public void registerDeployment(DeploymentWrapper<?> deployment) {
		System.out.println("Registering deployment " + deployment.getName());
		deployments.add(deployment);
	}

	public void unregisterDeployment(DeploymentWrapper<?> deployment) {
		System.out.println("Unregistering deployment " + deployment.getName());
		deployments.remove(deployment);
	}

	public void updateDeployment(DeploymentWrapper<?> deployment) {
		DeploymentWrapper<?> wrapper = getDeployment(deployment.getName());
		if (wrapper == null) return;
		deployments.removeIf(deploymentWrapper -> deploymentWrapper.getName().equals(deployment.getName()));

		deployments.add(deployment);
	}

	public List<DeploymentWrapper<?>> getDeployments() {
		return new ArrayList<>(deployments);
	}

	public DeploymentWrapper<?> getDeployment(String name) {
		for(DeploymentWrapper<?> deployment : deployments) if (deployment.getName().equals(name)) return deployment;
		return null;
	}

	public List<Game> getGames() {
		List<Game> games = new ArrayList<>();
		for(DeploymentWrapper<?> deployment : deployments) {
			if (deployment instanceof Game) {
				games.add((Game) deployment);
			}
		}
		return games;
	}

	public Game getGame(String name) {
		for(Game game : getGames()) if (game.getName().equals(name)) return game;
		return null;
	}

	public Proxy getProxy() {
		for(DeploymentWrapper<?> deployment : deployments) {
			if (deployment instanceof Proxy) {
				return (Proxy) deployment;
			}
		}
		return null;
	}

	public DeploymentWrapper<?> createWrapper(Deployment deployment, DeploymentType type) {
		return switch(type) {
			case SCALABLE, PERSISTENT -> new Game(deployment);
			case PROXY -> new Proxy(deployment);
			case PROCESS -> new Process(deployment);
		};
	}
}
