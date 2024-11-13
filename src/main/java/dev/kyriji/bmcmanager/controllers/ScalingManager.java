package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;
import dev.kyriji.bmcmanager.objects.Gamemode;
import dev.wiji.bigminecraftapi.BigMinecraftAPI;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;
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

	public ScaleResult checkToScale(Gamemode gamemode) {
		Gamemode.ScalingSettings settings = gamemode.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(gamemode);
			case TREND -> checkToScaleTrend(gamemode);
		};

		int instances = getActiveInstanceCount(gamemode);

		if(result == ScaleResult.UP && (instances >= settings.maxInstances || gamemode.isOnScaleUpCooldown())) return ScaleResult.NO_CHANGE;
		else if(result == ScaleResult.DOWN && (instances <= settings.minInstances || gamemode.isOnScaleDownCooldown())) return ScaleResult.NO_CHANGE;

		return result;
	}

	private ScaleResult checkToScaleThreshold(Gamemode gamemode) {
		int instances = getActiveInstanceCount(gamemode);
		int playerCount = getPlayerCount(gamemode);
		Gamemode.ScalingSettings settings = gamemode.getScalingSettings();

		double playersPerInstance = (double) playerCount / instances;

		if(playersPerInstance > settings.scaleUpThreshold) return ScaleResult.UP;
		else if(playersPerInstance < settings.scaleDownThreshold) return ScaleResult.DOWN;

		return ScaleResult.NO_CHANGE;
	}

	private ScaleResult checkToScaleTrend(Gamemode gamemode) {

		return ScaleResult.NO_CHANGE;
	}

	public void scale(Gamemode gamemode, ScaleResult result) {
		if(result == ScaleResult.NO_CHANGE) return;

		int targetInstances = getTargetInstances(gamemode, result);
		int currentInstances = getActiveInstanceCount(gamemode);

		if(targetInstances == currentInstances) return;
		else if(targetInstances < currentInstances) {
			scaleDownInstances(gamemode.getInstances(), targetInstances);
			gamemode.setLastScaleDown(System.currentTimeMillis());
		} else gamemode.setLastScaleUp(System.currentTimeMillis());

		/*
		We only need to handle scaling down deployments so that we can choose which instances to remove.
		Scaling up deployments is handled by the Kubernetes API.
		 */

		Deployment deployment = client.apps().deployments().inNamespace("default").withName(gamemode.getName()).get();
		int currentReplicas = deployment.getSpec().getReplicas();

		// This indicates the gamemode is disabled
		if(currentReplicas == 0) return;

		deployment.getSpec().setReplicas(targetInstances);
	}

	public int getTargetInstances(Gamemode gamemode, ScaleResult result) {
		int activeCurrentInstances = getActiveInstanceCount(gamemode);
		int playerCount = getPlayerCount(gamemode);

		int instancesToAdd = 0;

		Gamemode.ScalingSettings settings = gamemode.getScalingSettings();

		if(result == ScaleResult.UP) {
			int scaleUpLimit = settings.scaleUpLimit;

			double playersPerInstance = (double) playerCount / activeCurrentInstances;
			double targetRatio = settings.scaleUpThreshold;

			while(playersPerInstance < targetRatio && instancesToAdd < scaleUpLimit) {
				activeCurrentInstances++;
				instancesToAdd++;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}

		} else if(result == ScaleResult.DOWN) {
			int scaleDownLimit = settings.scaleDownLimit;

			double playersPerInstance = (double) playerCount / activeCurrentInstances;
			double targetRatio = settings.scaleDownThreshold;

			while(playersPerInstance > targetRatio && -instancesToAdd < scaleDownLimit) {
				activeCurrentInstances--;
				instancesToAdd--;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}
		}

		return gamemode.getInstances().size() + instancesToAdd;
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

	public int getPlayerCount(Gamemode gamemode) {
		List<MinecraftInstance> instances = gamemode.getInstances();
		int totalPlayers = 0;
		for (MinecraftInstance instance : instances) {
			totalPlayers += instance.getPlayers().size();
		}
		return totalPlayers;
	}

	public int getActiveInstanceCount(Gamemode gamemode) {
		return gamemode.getInstances().stream().filter(instance -> instance.getState() == InstanceState.RUNNING).toList().size();
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