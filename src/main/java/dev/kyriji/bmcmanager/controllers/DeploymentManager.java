package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.objects.Deployment;

import java.util.ArrayList;
import java.util.List;

public class DeploymentManager {

	public List<Deployment> deployments;

	public DeploymentManager() {
		this.deployments = new ArrayList<>();

		new Thread(() -> {
			while (true) {
				for(Deployment deployment : deployments) deployment.scale();
				try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
			}
		}).start();
	}

	public void registerDeployment(Deployment deployment) {
		System.out.println("Registering deployment " + deployment.getName());
		deployments.add(deployment);
	}

	public void unregisterDeployment(Deployment deployment) {
		System.out.println("Unregistering deployment " + deployment.getName());
		deployments.remove(deployment);
	}

	public List<Deployment> getDeployments() {
		return new ArrayList<>(deployments);
	}

	public Deployment getDeployment(String name) {
		for (Deployment deployment : deployments) {
			if (deployment.getName().equals(name)) return deployment;
		}
		return null;
	}
}
