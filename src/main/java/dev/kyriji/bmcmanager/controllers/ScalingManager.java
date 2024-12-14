package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;
import dev.kyriji.bmcmanager.interfaces.Scalable;
import dev.kyriji.bmcmanager.objects.ScalingSettings;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.PodResource;

import java.util.Comparator;
import java.util.List;

public class ScalingManager {

	private final KubernetesClient client;

	public ScalingManager() {
		this.client = new KubernetesClientBuilder().build();
	}

	public ScaleResult checkToScale(Scalable deployment) {
		ScalingSettings settings = deployment.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(deployment);
			case TREND -> checkToScaleTrend(deployment);
		};

		int instances = getActiveInstanceCount(deployment);

		if(result == ScaleResult.UP && (instances >= settings.maxInstances || deployment.isOnScaleUpCooldown())) return ScaleResult.NO_CHANGE;
		else if(result == ScaleResult.DOWN && (instances <= settings.minInstances || deployment.isOnScaleDownCooldown())) return ScaleResult.NO_CHANGE;

		return result;
	}

	private ScaleResult checkToScaleThreshold(Scalable deployment) {
		int instances = getActiveInstanceCount(deployment);
		int playerCount = getPlayerCount(deployment);
		ScalingSettings settings = deployment.getScalingSettings();

		double playersPerInstance = (double) playerCount / instances;

		if(playersPerInstance >= settings.scaleUpThreshold) return ScaleResult.UP;
		else if(playersPerInstance <= settings.scaleDownThreshold) return ScaleResult.DOWN;

		return ScaleResult.NO_CHANGE;
	}

	private ScaleResult checkToScaleTrend(Scalable deployment) {

		return ScaleResult.NO_CHANGE;
	}

	public void scale(Scalable deployment, ScaleResult result) {
		if(result == ScaleResult.NO_CHANGE) return;

		int targetInstances = getTargetInstances(deployment, result);
		int currentInstances = getActiveInstanceCount(deployment);

		if(targetInstances == currentInstances) return;
		else if(targetInstances < currentInstances) {
			scaleDownInstances(deployment.getInstances(), targetInstances);
			deployment.setLastScaleDown(System.currentTimeMillis());
		} else deployment.setLastScaleUp(System.currentTimeMillis());

		/*
		We only need to handle scaling down deployments so that we can choose which instances to remove.
		Scaling up deployments is handled by the Kubernetes API.
		 */

		Deployment k8sDeployment = client.apps().deployments().inNamespace("default").withName(deployment.getName()).get();
		int currentReplicas = k8sDeployment.getSpec().getReplicas();

		// This indicates the deployment is disabled
		if(currentReplicas == 0) return;

		client.apps().deployments().inNamespace("default").withName(deployment.getName()).scale(targetInstances);
	}

	public int getTargetInstances(Scalable deployment, ScaleResult result) {
		int activeCurrentInstances = getActiveInstanceCount(deployment);
		int playerCount = getPlayerCount(deployment);

		int instancesToAdd = 0;

		ScalingSettings settings = deployment.getScalingSettings();

		if(result == ScaleResult.UP) {
			int scaleUpLimit = settings.scaleUpLimit;

			double playersPerInstance = (double) playerCount / activeCurrentInstances;
			double targetRatio = settings.scaleUpThreshold;

			while(playersPerInstance <= targetRatio && instancesToAdd < scaleUpLimit) {
				activeCurrentInstances++;
				instancesToAdd++;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}

		} else if(result == ScaleResult.DOWN) {
			int scaleDownLimit = settings.scaleDownLimit;

			double playersPerInstance = (double) playerCount / activeCurrentInstances;
			double targetRatio = settings.scaleDownThreshold;

			while(playersPerInstance < targetRatio && -instancesToAdd < scaleDownLimit) {
				activeCurrentInstances--;
				instancesToAdd--;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}
		}

		return deployment.getInstances().size() + instancesToAdd;
	}

	public void scaleDownInstances(List<MinecraftInstance> instances, int targetSize) {
		if(instances.size() <= targetSize) return;

		int instancesToRemove = instances.size() - targetSize;

		instances.sort(Comparator.comparingInt(instance -> instance.getPlayers().size()));

		for(MinecraftInstance instance : instances.subList(0, instancesToRemove)) {
			PodResource pod = client.pods().inNamespace("default").withName(instance.getPodName());
			pod.delete();

			//TODO: Set pod state to Stopping
		}
	}

	public int getPlayerCount(Scalable deployment) {
		List<MinecraftInstance> instances = deployment.getInstances();
		int totalPlayers = 0;
		for (MinecraftInstance instance : instances) {
			totalPlayers += instance.getPlayers().size();
		}
		return totalPlayers;
	}

	public int getActiveInstanceCount(Scalable deployment) {
		return deployment.getInstances().stream().filter(instance -> instance.getState() == InstanceState.RUNNING).toList().size();
	}
}


/* Scaling config options
- maxPlayers
- minimumInstances
- maximumInstances

- scaleUpThreshold
- scaleDownThreshold

- scaleUpCooldown
- scaleDownCooldown


Scaling Modes:
- Threshold
	- Scale up when player count per instance exceeds scaleUpThreshold
- Trend
	- Scale up based on queuing trends
*/