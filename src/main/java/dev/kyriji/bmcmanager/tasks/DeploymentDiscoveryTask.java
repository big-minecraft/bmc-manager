package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.DeploymentManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.DeploymentType;
import dev.kyriji.bmcmanager.objects.BMCDeployment;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
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

		List<DeploymentWrapper<?>> existingDeployments = deploymentManager.getDeployments();
		List<DeploymentWrapper<?>> newDeployments = new ArrayList<>();

		List<Deployment> deployments = client.apps().deployments()
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

		List<StatefulSet> statefulSets = client.apps().statefulSets()
				.inNamespace("default")
				.list()
				.getItems()
				.stream()
				.filter(statefulSet ->
						statefulSet.getSpec() != null &&
								statefulSet.getSpec().getTemplate() != null &&
								statefulSet.getSpec().getTemplate().getMetadata() != null &&
								statefulSet.getSpec().getTemplate().getMetadata().getLabels() != null &&
								"true".equals(statefulSet.getSpec().getTemplate().getMetadata().getLabels()
										.get(DeploymentLabel.SERVER_DISCOVERY.getLabel())))
				.toList();

		List<BMCDeployment> bmcDeployments = new ArrayList<>();
		deployments.forEach(deployment -> bmcDeployments.add(new BMCDeployment(deployment)));
		statefulSets.forEach(statefulSet -> bmcDeployments.add(new BMCDeployment(statefulSet)));

		bmcDeployments.forEach(deployment -> {
			String typeLabel = deployment.getSpec().getTemplate().getMetadata().getLabels().get(DeploymentLabel.DEPLOYMENT_TYPE.getLabel());
			DeploymentType type = DeploymentType.getType(typeLabel);

			if(type == null) return;
			DeploymentWrapper<?> deploymentWrapper = deploymentManager.createWrapper(deployment, type);

			if(existingDeployments.contains(deploymentWrapper)) {
				existingDeployments.remove(deploymentWrapper);
				deploymentManager.updateDeployment(deploymentWrapper);
			} else {
				newDeployments.add(deploymentWrapper);
			}
		});

		for(DeploymentWrapper<?> existingDeployment : existingDeployments) deploymentManager.unregisterDeployment(existingDeployment);
		for(DeploymentWrapper<?> newDeployment : newDeployments) deploymentManager.registerDeployment(newDeployment);
		for(DeploymentWrapper<?> deployment : deploymentManager.getDeployments()) deployment.fetchInstances();
	}
}