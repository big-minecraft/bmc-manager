package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.kyriji.bmcmanager.objects.ScalingSettings;
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

	public ScaleResult checkToScale(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		ScalingSettings settings = deploymentWrapper.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(deploymentWrapper);
			case TREND -> checkToScaleTrend(deploymentWrapper);
		};

		int instances = getActiveInstanceCount(deploymentWrapper);

		if(result == ScaleResult.UP && (instances >= settings.maxInstances || deploymentWrapper.isOnScaleUpCooldown()))
			return ScaleResult.NO_CHANGE;
		else if(result == ScaleResult.DOWN && (instances <= settings.minInstances || deploymentWrapper.isOnScaleDownCooldown()))
			return ScaleResult.NO_CHANGE;

		return result;
	}

	private ScaleResult checkToScaleThreshold(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		int instances = getActiveInstanceCount(deploymentWrapper);
		int playerCount = getPlayerCount(deploymentWrapper);
		ScalingSettings settings = deploymentWrapper.getScalingSettings();

		double playersPerInstance = (double) playerCount / instances;

		if(playersPerInstance >= settings.scaleUpThreshold) return ScaleResult.UP;
		else if(playersPerInstance <= settings.scaleDownThreshold) return ScaleResult.DOWN;

		return ScaleResult.NO_CHANGE;
	}

	private ScaleResult checkToScaleTrend(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {

		return ScaleResult.NO_CHANGE;
	}

	public void scale(DeploymentWrapper<MinecraftInstance> deploymentWrapper, ScaleResult result) {
		if(result == ScaleResult.NO_CHANGE) return;

		int targetInstances = getTargetInstances(deploymentWrapper, result);
		int currentInstances = getActiveInstanceCount(deploymentWrapper);

		if(targetInstances == currentInstances) return;
		else if(targetInstances < currentInstances) {
			scaleDownInstances(deploymentWrapper, targetInstances);
			deploymentWrapper.setLastScaleDown(System.currentTimeMillis());
		} else deploymentWrapper.setLastScaleUp(System.currentTimeMillis());

		/*
		We only need to handle scaling down games so that we can choose which instances to remove.
		Scaling up games is handled by the Kubernetes API.
		 */

		Deployment k8sDeployment = client.apps().deployments().inNamespace("default").withName(deploymentWrapper.getName()).get();
		int currentReplicas = k8sDeployment.getSpec().getReplicas();

		// This indicates the deployment is disabled
		if(currentReplicas == 0) return;

		client.apps().deployments().inNamespace("default").withName(deploymentWrapper.getName()).scale(targetInstances);
	}

	public int getTargetInstances(DeploymentWrapper<MinecraftInstance> deploymentWrapper, ScaleResult result) {
		int activeCurrentInstances = getActiveInstanceCount(deploymentWrapper);

		int playerCount = getPlayerCount(deploymentWrapper);

		int instancesToAdd = 0;

		ScalingSettings settings = deploymentWrapper.getScalingSettings();

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

		return deploymentWrapper.getInstances().size() + instancesToAdd;
	}

	public void scaleDownInstances(DeploymentWrapper<MinecraftInstance> deploymentWrapper, int targetSize) {
		List<MinecraftInstance> instances = deploymentWrapper.getInstances();
		if(instances.size() <= targetSize) return;

		int instancesToRemove = instances.size() - targetSize;

		instances.sort(Comparator.comparingInt(instance -> instance.getPlayers().size()));

		Gson gson = new Gson();

		for(Instance instance : instances.subList(0, instancesToRemove)) {
			PodResource pod = client.pods().inNamespace("default").withName(instance.getPodName());
			pod.delete();

			instance.setState(InstanceState.STOPPING);
			RedisManager.get().updateInstance(instance);
		}
	}

	public int getPlayerCount(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		List<MinecraftInstance> instances = deploymentWrapper.getInstances();

		return instances.stream().mapToInt(instance -> instance.getPlayers().size()).sum();
	}

	public int getActiveInstanceCount(DeploymentWrapper<? extends Instance> deploymentWrapper) {
		return deploymentWrapper.getInstances().stream().filter(instance -> instance.getState() == InstanceState.RUNNING).toList().size();
	}

	public void turnOffPod(MinecraftInstance instance) {
		PodResource pod = client.pods().inNamespace("default").withName(instance.getPodName());
		pod.delete();
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