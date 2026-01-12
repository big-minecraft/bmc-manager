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
	// ============ DEBUG FLAG ============
	// Set to false to disable all scaling debug output
	private static final boolean DEBUG_SCALING = true;
	// ====================================

	public ScalingDecision determineScalingAction(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		ScalingSettings settings = deploymentWrapper.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		if (DEBUG_SCALING) {
			System.out.println("\n========== SCALING DECISION START ==========");
			System.out.println("Deployment: " + deploymentWrapper.getName());
			System.out.println("Strategy: " + strategy);
		}

		// Check what scaling action is needed
		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(deploymentWrapper);
			case TREND -> checkToScaleTrend(deploymentWrapper);
		};

		int currentInstances = deploymentWrapper.getInstances().size();

		if (DEBUG_SCALING) {
			System.out.println("Current active instances: " + currentInstances);
			System.out.println("Initial decision: " + result);
		}

		// Check constraints (min/max instances and cooldowns)
		if(result == ScaleResult.UP && (currentInstances >= settings.maxInstances || deploymentWrapper.isOnScaleUpCooldown())) {
			if (DEBUG_SCALING) {
				System.out.println("Scale-up BLOCKED:");
				if (currentInstances >= settings.maxInstances) {
					System.out.println("  - At max instances (" + currentInstances + " >= " + settings.maxInstances + ")");
				}
				if (deploymentWrapper.isOnScaleUpCooldown()) {
					System.out.println("  - On scale-up cooldown");
				}
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(currentInstances);
		} else if(result == ScaleResult.DOWN && (currentInstances <= settings.minInstances || deploymentWrapper.isOnScaleDownCooldown())) {
			if (DEBUG_SCALING) {
				System.out.println("Scale-down BLOCKED:");
				if (currentInstances <= settings.minInstances) {
					System.out.println("  - At min instances (" + currentInstances + " <= " + settings.minInstances + ")");
				}
				if (deploymentWrapper.isOnScaleDownCooldown()) {
					System.out.println("  - On scale-down cooldown");
				}
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(currentInstances);
		}

		// If no change needed, return early
		if(result == ScaleResult.NO_CHANGE) {
			if (DEBUG_SCALING) {
				System.out.println("No scaling needed based on thresholds");
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(currentInstances);
		}

		// Calculate target replicas
		int targetReplicas = calculateTargetReplicas(deploymentWrapper, result);

		// If target equals current, no change needed
		if(targetReplicas == currentInstances) {
			if (DEBUG_SCALING) {
				System.out.println("Target replicas (" + targetReplicas + ") equals current (" + currentInstances + "), no change needed");
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(currentInstances);
		}

		// For scale-down, select specific pods to delete
		if(result == ScaleResult.DOWN) {
			int podsToRemove = currentInstances - targetReplicas;
			if (DEBUG_SCALING) {
				System.out.println("Scaling DOWN: " + currentInstances + " -> " + targetReplicas + " (removing " + podsToRemove + " instances)");
			}
			List<MinecraftInstance> podsToDelete = selectPodsForScaleDown(deploymentWrapper.getInstances(), podsToRemove);
			if (DEBUG_SCALING) {
				System.out.println("========== SCALING DECISION END (SCALE DOWN) ==========\n");
			}
			return ScalingDecision.scaleDown(currentInstances, targetReplicas, podsToDelete);
		} else {
			// Scale up - no specific pods to select
			int podsToAdd = targetReplicas - currentInstances;
			if (DEBUG_SCALING) {
				System.out.println("Scaling UP: " + currentInstances + " -> " + targetReplicas + " (adding " + podsToAdd + " instances)");
				System.out.println("========== SCALING DECISION END (SCALE UP) ==========\n");
			}
			return ScalingDecision.scaleUp(currentInstances, targetReplicas);
		}
	}

	private ScaleResult checkToScaleThreshold(DeploymentWrapper<MinecraftInstance> deploymentWrapper) {
		int totalInstances = deploymentWrapper.getInstances().size();
		int activeInstances = getActiveInstanceCount(deploymentWrapper);
		int playerCount = getPlayerCount(deploymentWrapper);
		ScalingSettings settings = deploymentWrapper.getScalingSettings();

		if (DEBUG_SCALING) {
			System.out.println("\n--- Threshold Check ---");
			System.out.println("Total players: " + playerCount);
			System.out.println("Total instances: " + totalInstances);
			System.out.println("Active (RUNNING) instances: " + activeInstances);
		}

		// Enforce minimum instances: scale up if below minimum ACTIVE instances
		if(activeInstances < settings.minInstances) {
			if (DEBUG_SCALING) {
				System.out.println("Decision: SCALE UP (below minimum: " + activeInstances + " < " + settings.minInstances + ")");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.UP;
		}

		// Avoid division by zero
		if(activeInstances == 0) {
			if (DEBUG_SCALING) {
				System.out.println("No active instances, cannot calculate ratio");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.NO_CHANGE;
		}

		double playersPerInstance = (double) playerCount / activeInstances;

		if (DEBUG_SCALING) {
			System.out.println("Players per instance: " + String.format("%.2f", playersPerInstance));
			System.out.println("Scale-up threshold: " + settings.scaleUpThreshold);
			System.out.println("Scale-down threshold: " + settings.scaleDownThreshold);
		}

		if(playersPerInstance >= settings.scaleUpThreshold) {
			if (DEBUG_SCALING) {
				System.out.println("Decision: SCALE UP (" + String.format("%.2f", playersPerInstance) + " >= " + settings.scaleUpThreshold + ")");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.UP;
		} else if(playersPerInstance < settings.scaleDownThreshold) {
			if (DEBUG_SCALING) {
				System.out.println("Decision: SCALE DOWN (" + String.format("%.2f", playersPerInstance) + " < " + settings.scaleDownThreshold + ")");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.DOWN;
		}

		if (DEBUG_SCALING) {
			System.out.println("Decision: NO CHANGE (within thresholds)");
			System.out.println("--- End Threshold Check ---");
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

		if (DEBUG_SCALING) {
			System.out.println("\n--- Calculating Target Replicas ---");
		}

		if(result == ScaleResult.UP) {
			int scaleUpLimit = settings.scaleUpLimit;
			double targetRatio = settings.scaleUpThreshold;

			// Special case: if we have 0 active instances, add up to minimum (respecting scaleUpLimit)
			if(activeCurrentInstances == 0) {
				instancesToAdd = Math.min(settings.minInstances, scaleUpLimit);
				if (DEBUG_SCALING) {
					System.out.println("Scale-up calculation (from 0 active instances):");
					System.out.println("  Adding " + instancesToAdd + " instances to reach minimum");
				}
			} else {
				// Calculate how many instances to add (up to scaleUpLimit)
				double playersPerInstance = (double) playerCount / activeCurrentInstances;

				if (DEBUG_SCALING) {
					System.out.println("Scale-up calculation:");
					System.out.println("  Starting ratio: " + String.format("%.2f", playersPerInstance));
					System.out.println("  Target ratio: " + targetRatio);
					System.out.println("  Scale-up limit: " + scaleUpLimit);
				}

				while(playersPerInstance >= targetRatio && instancesToAdd < scaleUpLimit) {
					activeCurrentInstances++;
					instancesToAdd++;
					playersPerInstance = (double) playerCount / activeCurrentInstances;
				}

				if (DEBUG_SCALING) {
					System.out.println("  Instances to add: " + instancesToAdd);
					System.out.println("  Final ratio: " + String.format("%.2f", playersPerInstance));
				}
			}

		} else if(result == ScaleResult.DOWN) {
			int scaleDownLimit = settings.scaleDownLimit;
			double targetRatio = settings.scaleDownThreshold;

			// Calculate how many instances to remove (up to scaleDownLimit)
			double playersPerInstance = (double) playerCount / activeCurrentInstances;

			if (DEBUG_SCALING) {
				System.out.println("Scale-down calculation:");
				System.out.println("  Starting ratio: " + String.format("%.2f", playersPerInstance));
				System.out.println("  Target ratio: " + targetRatio);
				System.out.println("  Scale-down limit: " + scaleDownLimit);
			}

			while(playersPerInstance < targetRatio && -instancesToAdd < scaleDownLimit && activeCurrentInstances > 1) {
				activeCurrentInstances--;
				instancesToAdd--;
				playersPerInstance = (double) playerCount / activeCurrentInstances;
			}

			if (DEBUG_SCALING) {
				System.out.println("  Instances to remove: " + (-instancesToAdd));
				System.out.println("  Final ratio: " + String.format("%.2f", playersPerInstance));
			}
		}

		// Calculate target replicas = total existing instances + instances to add/remove
		// instancesToAdd is based on ACTIVE (RUNNING) instances, but we add to total count
		// This ensures we account for BLOCKED/STARTING/STOPPING instances in the replica count
		int targetReplicas = deploymentWrapper.getInstances().size() + instancesToAdd;

		if (DEBUG_SCALING) {
			System.out.println("Target replicas: " + targetReplicas);
			System.out.println("--- End Calculating Target Replicas ---");
		}

		return targetReplicas;
	}

	private List<MinecraftInstance> selectPodsForScaleDown(List<MinecraftInstance> instances, int count) {
		if(instances.size() <= count) return new ArrayList<>();

		if (DEBUG_SCALING) {
			System.out.println("\n--- Selecting Pods for Scale-Down ---");
		}

		// Sort by player count (ascending) - instances with fewest players first
		List<MinecraftInstance> sortedInstances = new ArrayList<>(instances);
		sortedInstances.sort(Comparator.comparingInt(instance -> instance.getPlayers().size()));

		// Filter to only include instances that are safe to remove
		List<MinecraftInstance> candidates = sortedInstances.stream()
			.filter(instance -> instance.getState() == InstanceState.RUNNING)
			.toList();

		if (DEBUG_SCALING) {
			candidates.forEach(instance ->
				System.out.println("  Candidate: " + instance.getName() + ", Players: " + instance.getPlayers().size())
			);
		}

		// Take the first N candidates (those with fewest players)
		int toRemove = Math.min(count, candidates.size());

		if (DEBUG_SCALING) {
			System.out.println("Selected " + toRemove + " instances for removal");
			System.out.println("--- End Selecting Pods ---");
		}

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
