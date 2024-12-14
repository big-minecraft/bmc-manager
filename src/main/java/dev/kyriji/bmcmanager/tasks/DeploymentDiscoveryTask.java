package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.DeploymentManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.objects.Deployment;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import redis.clients.jedis.JedisPubSub;

import java.util.*;

public class DeploymentDiscoveryTask {
	private final KubernetesClient client;

	public DeploymentDiscoveryTask() {
		this.client = new KubernetesClientBuilder().build();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					discoverDeployments();
				}
			}, RedisChannel.DEPLOYMENT_MODIFIED.getRef());
		}).start();

		new Thread(() -> {
			//Sleep to ensure that ServerDiscoveryTask has had time to register all instances
			try { Thread.sleep(5000); } catch(InterruptedException e) { throw new RuntimeException(e); }
			discoverDeployments();
		}).start();
	}

	public void discoverDeployments() {
		DeploymentManager deploymentManager = BMCManager.deploymentManager;

		List<Deployment> existingDeployments = deploymentManager.getDeployments();
		List<Deployment> newDeployments = new ArrayList<>();

		List<io.fabric8.kubernetes.api.model.apps.Deployment> k8sDeployments = client.apps().deployments()
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

		k8sDeployments.forEach(k8sDeployment -> {
			Deployment deployment = new Deployment(k8sDeployment);
			if(existingDeployments.contains(deployment)) {
				existingDeployments.remove(deployment);
			} else {
				newDeployments.add(deployment);
			}
		});

		for(Deployment existingDeployment : existingDeployments) deploymentManager.unregisterDeployment(existingDeployment);
		for(Deployment newDeployment : newDeployments) deploymentManager.registerDeployment(newDeployment);
		for(Deployment deployment : deploymentManager.getDeployments()) deployment.fetchInstances();
	}
}