package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
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

	public ScaleResult checkToScale(Scalable scalableGame) {
		ScalingSettings settings = scalableGame.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(scalableGame);
			case TREND -> checkToScaleTrend(scalableGame);
		};

		int instances = getActiveInstanceCount(scalableGame);

		if(result == ScaleResult.UP && (instances >= settings.maxInstances || scalableGame.isOnScaleUpCooldown()))
			return ScaleResult.NO_CHANGE;
		else if(result == ScaleResult.DOWN && (instances <= settings.minInstances || scalableGame.isOnScaleDownCooldown()))
			return ScaleResult.NO_CHANGE;

		return result;
	}

	private ScaleResult checkToScaleThreshold(Scalable scalableGame) {
		int instances = getActiveInstanceCount(scalableGame);
		int playerCount = getPlayerCount(scalableGame);
		ScalingSettings settings = scalableGame.getScalingSettings();

		double playersPerInstance = (double) playerCount / instances;

		if(playersPerInstance >= settings.scaleUpThreshold) return ScaleResult.UP;
		else if(playersPerInstance <= settings.scaleDownThreshold) return ScaleResult.DOWN;

		return ScaleResult.NO_CHANGE;
	}

	private ScaleResult checkToScaleTrend(Scalable scalableGame) {

		return ScaleResult.NO_CHANGE;
	}

	public void scale(Scalable scalableGame, ScaleResult result) {
		if(result == ScaleResult.NO_CHANGE) return;

		int targetInstances = getTargetInstances(scalableGame, result);
		int currentInstances = getActiveInstanceCount(scalableGame);

		if(targetInstances == currentInstances) return;
		else if(targetInstances < currentInstances) {
			scaleDownInstances(scalableGame.getInstances(), targetInstances);
			scalableGame.setLastScaleDown(System.currentTimeMillis());
		} else scalableGame.setLastScaleUp(System.currentTimeMillis());

		/*
		We only need to handle scaling down games so that we can choose which instances to remove.
		Scaling up games is handled by the Kubernetes API.
		 */

		Deployment k8sDeployment = client.apps().deployments().inNamespace("default").withName(scalableGame.getName()).get();
		int currentReplicas = k8sDeployment.getSpec().getReplicas();

		// This indicates the deployment is disabled
		if(currentReplicas == 0) return;

		client.apps().deployments().inNamespace("default").withName(scalableGame.getName()).scale(targetInstances);
	}

	public int getTargetInstances(Scalable scalableGame, ScaleResult result) {
		int activeCurrentInstances = getActiveInstanceCount(scalableGame);
		int playerCount = getPlayerCount(scalableGame);

		int instancesToAdd = 0;

		ScalingSettings settings = scalableGame.getScalingSettings();

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

		return scalableGame.getInstances().size() + instancesToAdd;
	}

	public void scaleDownInstances(List<MinecraftInstance> instances, int targetSize) {
		if(instances.size() <= targetSize) return;

		int instancesToRemove = instances.size() - targetSize;

		instances.sort(Comparator.comparingInt(instance -> instance.getPlayers().size()));
		Gson gson = new Gson();

		for(MinecraftInstance instance : instances.subList(0, instancesToRemove)) {
			PodResource pod = client.pods().inNamespace("default").withName(instance.getPodName());
			pod.delete();

			instance.setState(InstanceState.STOPPING);
			RedisManager.get().hset("instances", instance.getUid(), gson.toJson(instance));
		}
	}

	public int getPlayerCount(Scalable scalableGame) {
		List<MinecraftInstance> instances = scalableGame.getInstances();
		int totalPlayers = 0;
		for (MinecraftInstance instance : instances) {
			totalPlayers += instance.getPlayers().size();
		}
		return totalPlayers;
	}

	public int getActiveInstanceCount(Scalable scalableGame) {
		return scalableGame.getInstances().stream().filter(instance -> instance.getState() == InstanceState.RUNNING).toList().size();
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