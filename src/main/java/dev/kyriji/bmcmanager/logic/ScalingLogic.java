package dev.kyriji.bmcmanager.logic;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.kyriji.bmcmanager.objects.ScalingDecision;
import dev.kyriji.bmcmanager.objects.ScalingSettings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScalingLogic {

	public ScalingDecision determineScalingAction(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		ScalingSettings settings = deploymentWrapper.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		// Check what scaling action is needed
		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(deploymentWrapper);
			case TREND -> checkToScaleTrend(deploymentWrapper);
		};

		int currentInstances = getActiveInstanceCount(deploymentWrapper);

		// Check constraints (min/max instances and cooldowns)
		if(result == ScaleResult.UP && (currentInstances >= settings.maxInstances || deploymentWrapper.isOnScaleUpCooldown())) {
			return ScalingDecision.noChange(currentInstances);
		} else if(result == ScaleResult.DOWN && (currentInstances <= settings.minInstances || deploymentWrapper.isOnScaleDownCooldown())) {
			return ScalingDecision.noChange(currentInstances);
		}

		// If no change needed, return early
		if(result == ScaleResult.NO_CHANGE) {
			return ScalingDecision.noChange(currentInstances);
		}

		// Calculate target replicas
		int targetReplicas = calculateTargetReplicas(deploymentWrapper, result);

		// If target equals current, no change needed
		if(targetReplicas == currentInstances) {
			return ScalingDecision.noChange(currentInstances);
		}

		// For scale-down, select specific pods to delete
		if(result == ScaleResult.DOWN) {
			int podsToRemove = currentInstances - targetReplicas;
			List<MinecraftInstance> podsToDelete = selectPodsForScaleDown(deploymentWrapper.getInstances(), podsToRemove);
			return ScalingDecision.scaleDown(currentInstances, targetReplicas, podsToDelete);
		} else {
			// Scale up - no specific pods to select
			return ScalingDecision.scaleUp(currentInstances, targetReplicas);
		}
	}

	private ScaleResult checkToScaleThreshold(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		int instances = getActiveInstanceCount(deploymentWrapper);
		int playerCount = getPlayerCount(deploymentWrapper);
		ScalingSettings settings = deploymentWrapper.getScalingSettings();

		// Avoid division by zero
		if(instances == 0) {
			return ScaleResult.NO_CHANGE;
		}

		double playersPerInstance = (double) playerCount / instances;

		if(playersPerInstance >= settings.scaleUpThreshold) {
			return ScaleResult.UP;
		} else if(playersPerInstance <= settings.scaleDownThreshold) {
			return ScaleResult.DOWN;
		}

		return ScaleResult.NO_CHANGE;
	}

	private ScaleResult checkToScaleTrend(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		// Trend-based scaling not yet implemented
		return ScaleResult.NO_CHANGE;
	}

	private int calculateTargetReplicas(DeploymentWrapper<MinecraftInstance> deploymentWrapper, ScaleResult result) {
		int activeCurrentInstances = getActiveInstanceCount(deploymentWrapper);
		int playerCount = getPlayerCount(deploymentWrapper);
		int instancesToAdd = 0;

		ScalingSettings settings = deploymentWrapper.getScalingSettings();

		if(result == ScaleResult.UP) {
			int scaleUpLimit = settings.scaleUpLimit;
			double targetRatio = settings.scaleUpThreshold;

			// Calculate how many instances to add (up to scaleUpLimit)
			double playersPerInstance = (double) playerCount / activeCurrentInstances;
			while(playersPerInstance > targetRatio && instancesToAdd < scaleUpLimit) {
				activeCurrentInstances++;
				instancesToAdd++;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}

		} else if(result == ScaleResult.DOWN) {
			int scaleDownLimit = settings.scaleDownLimit;
			double targetRatio = settings.scaleDownThreshold;

			// Calculate how many instances to remove (up to scaleDownLimit)
			double playersPerInstance = (double) playerCount / activeCurrentInstances;
			while(playersPerInstance < targetRatio && -instancesToAdd < scaleDownLimit && activeCurrentInstances > 1) {
				activeCurrentInstances--;
				instancesToAdd--;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}
		}

		return deploymentWrapper.getInstances().size() + instancesToAdd;
	}

	private List<MinecraftInstance> selectPodsForScaleDown(List<MinecraftInstance> instances, int count) {
		if(instances.size() <= count) return new ArrayList<>();

		// Sort by player count (ascending) - instances with fewest players first
		List<MinecraftInstance> sortedInstances = new ArrayList<>(instances);
		sortedInstances.sort(Comparator.comparingInt(instance -> instance.getPlayers().size()));

		// Filter to only include instances that are safe to remove
		List<MinecraftInstance> candidates = sortedInstances.stream()
			.filter(instance -> instance.getState() == InstanceState.RUNNING)
			.toList();

		// Take the first N candidates (those with fewest players)
		int toRemove = Math.min(count, candidates.size());
		return new ArrayList<>(candidates.subList(0, toRemove));
	}

	private int getPlayerCount(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		List<MinecraftInstance> instances = deploymentWrapper.getInstances();
		return instances.stream().mapToInt(instance -> instance.getPlayers().size()).sum();
	}

	private int getActiveInstanceCount(DeploymentWrapper<? extends Instance> deploymentWrapper) {
		return (int) deploymentWrapper.getInstances().stream()
			.filter(instance -> instance.getState() == InstanceState.RUNNING)
			.count();
	}
}
