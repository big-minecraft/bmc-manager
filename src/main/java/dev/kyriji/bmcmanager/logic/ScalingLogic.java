package dev.kyriji.bmcmanager.logic;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
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

	public ScalingDecision determineScalingAction(GameServerWrapper<MinecraftInstance> gameServerWrapper, int currentPodCount) {
		ScalingSettings settings = gameServerWrapper.getScalingSettings();
		ScaleStrategy strategy = settings.strategy;

		if (DEBUG_SCALING) {
			System.out.println("\n========== SCALING DECISION START ==========");
			System.out.println("GameServer: " + gameServerWrapper.getName());
			System.out.println("Strategy: " + strategy);
			System.out.println("Current pod count from K8s: " + currentPodCount);
			System.out.println("All instances in wrapper:");
			for (MinecraftInstance inst : gameServerWrapper.getInstances()) {
				System.out.println("  - " + inst.getName() + " (" + inst.getPodName() + "): state=" + inst.getState() + ", players=" + inst.getPlayers().size());
			}
		}

		// If K8s already has enough pods, don't create more even if Redis hasn't caught up
		// Only wait if Redis shows NO instances at all (not yet discovered)
		int totalInstancesInRedis = gameServerWrapper.getInstances().size();
		if (currentPodCount >= settings.minInstances && totalInstancesInRedis == 0 && currentPodCount > 0) {
			if (DEBUG_SCALING) {
				System.out.println("K8s has " + currentPodCount + " pods but Redis shows 0 instances - waiting for discovery");
				System.out.println("========== SCALING DECISION END (NO CHANGE - WAITING) ==========\n");
			}
			return ScalingDecision.noChange(currentPodCount);
		}

		// Check what scaling action is needed
		ScaleResult result = switch(strategy) {
			case THRESHOLD -> checkToScaleThreshold(gameServerWrapper);
			case TREND -> checkToScaleTrend(gameServerWrapper);
		};

		int totalInstances = getTotalNonTerminatingInstanceCount(gameServerWrapper);
		int activeInstances = getActiveInstanceCount(gameServerWrapper);

		if (DEBUG_SCALING) {
			System.out.println("Total instances: " + totalInstances);
			System.out.println("Active (RUNNING+STARTING) instances: " + activeInstances);
			System.out.println("Initial decision: " + result);
		}

		// Check constraints (min/max instances and cooldowns)
		// Bypass cooldown when below minimum instances - we need to recover ASAP
		boolean belowMinimum = activeInstances < settings.minInstances;
		if(result == ScaleResult.UP && (totalInstances >= settings.maxInstances || (!belowMinimum && gameServerWrapper.isOnScaleUpCooldown()))) {
			if (DEBUG_SCALING) {
				System.out.println("Scale-up BLOCKED:");
				if (totalInstances >= settings.maxInstances) {
					System.out.println("  - At max instances (" + totalInstances + " >= " + settings.maxInstances + ")");
				}
				if (!belowMinimum && gameServerWrapper.isOnScaleUpCooldown()) {
					System.out.println("  - On scale-up cooldown");
				}
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(totalInstances);
		} else if(result == ScaleResult.DOWN && (activeInstances <= settings.minInstances || gameServerWrapper.isOnScaleDownCooldown())) {
			if (DEBUG_SCALING) {
				System.out.println("Scale-down BLOCKED:");
				if (activeInstances <= settings.minInstances) {
					System.out.println("  - At min active instances (" + activeInstances + " <= " + settings.minInstances + ")");
				}
				if (gameServerWrapper.isOnScaleDownCooldown()) {
					System.out.println("  - On scale-down cooldown");
				}
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(totalInstances);
		}

		// If no change needed, return early
		if(result == ScaleResult.NO_CHANGE) {
			if (DEBUG_SCALING) {
				System.out.println("No scaling needed based on thresholds");
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(totalInstances);
		}

		// Calculate target replicas
		int targetReplicas = calculateTargetReplicas(gameServerWrapper, result);

		// If target equals current, no change needed
		if(targetReplicas == totalInstances) {
			if (DEBUG_SCALING) {
				System.out.println("Target replicas (" + targetReplicas + ") equals current (" + totalInstances + "), no change needed");
				System.out.println("========== SCALING DECISION END (NO CHANGE) ==========\n");
			}
			return ScalingDecision.noChange(totalInstances);
		}

		// For scale-down, select specific pods to delete
		if(result == ScaleResult.DOWN) {
			int podsToRemove = totalInstances - targetReplicas;
			if (DEBUG_SCALING) {
				System.out.println("Scaling DOWN: " + totalInstances + " -> " + targetReplicas + " (removing " + podsToRemove + " instances)");
			}
			List<MinecraftInstance> podsToDelete = selectPodsForScaleDown(gameServerWrapper.getInstances(), podsToRemove);
			if (DEBUG_SCALING) {
				System.out.println("========== SCALING DECISION END (SCALE DOWN) ==========\n");
			}
			return ScalingDecision.scaleDown(totalInstances, targetReplicas, podsToDelete);
		} else {
			// Scale up - no specific pods to select
			// Use K8s pod count to avoid creating duplicates while waiting for Redis discovery
			int podsToAdd = targetReplicas - Math.max(totalInstances, currentPodCount);
			if (podsToAdd <= 0) {
				if (DEBUG_SCALING) {
					System.out.println("K8s already has " + currentPodCount + " pods (target: " + targetReplicas + "), no scale-up needed");
					System.out.println("========== SCALING DECISION END (NO CHANGE - K8S AHEAD) ==========\n");
				}
				return ScalingDecision.noChange(currentPodCount);
			}
			if (DEBUG_SCALING) {
				System.out.println("Scaling UP: " + currentPodCount + " -> " + targetReplicas + " (adding " + podsToAdd + " instances)");
				System.out.println("========== SCALING DECISION END (SCALE UP) ==========\n");
			}
			return ScalingDecision.scaleUp(currentPodCount, targetReplicas);
		}
	}

	private ScaleResult checkToScaleThreshold(GameServerWrapper<MinecraftInstance> gameServerWrapper) {
		int totalInstances = getTotalNonTerminatingInstanceCount(gameServerWrapper);
		int activeInstances = getActiveInstanceCount(gameServerWrapper);
		int runningInstances = getRunningInstanceCount(gameServerWrapper);
		int startingInstances = activeInstances - runningInstances;
		int playerCount = getPlayerCount(gameServerWrapper);
		ScalingSettings settings = gameServerWrapper.getScalingSettings();

		if (DEBUG_SCALING) {
			System.out.println("\n--- Threshold Check ---");
			System.out.println("Total players: " + playerCount);
			System.out.println("Total instances: " + totalInstances);
			System.out.println("Active (RUNNING+STARTING) instances: " + activeInstances);
			System.out.println("Running instances: " + runningInstances);
			System.out.println("Starting instances: " + startingInstances);
		}

		// PERSISTENT deployments use ReadWriteOnce volumes that can only be mounted by one pod
		// at a time. If an instance is draining, block scale-up until it is fully gone so the
		// replacement pod does not fail trying to claim the still-held volume.
		if ("PERSISTENT".equals(gameServerWrapper.getDeploymentType()) &&
				hasDrainingInstances(gameServerWrapper)) {
			if (DEBUG_SCALING) {
				System.out.println("Decision: NO CHANGE (PERSISTENT deployment has a draining instance - waiting for volume release)");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.NO_CHANGE;
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

		// For scale-up: use all active instances (RUNNING + STARTING) - considers provisioned capacity
		double playersPerActiveInstance = (double) playerCount / activeInstances;

		// For scale-down: only use RUNNING instances - don't scale down while capacity is still starting
		// This prevents killing servers with players while waiting for new ones to boot
		double playersPerRunningInstance = runningInstances > 0 ? (double) playerCount / runningInstances : 0;

		if (DEBUG_SCALING) {
			System.out.println("Players per active instance (for scale-up): " + String.format("%.2f", playersPerActiveInstance));
			System.out.println("Players per running instance (for scale-down): " + String.format("%.2f", playersPerRunningInstance));
			System.out.println("Scale-up threshold: " + settings.scaleUpThreshold);
			System.out.println("Scale-down threshold: " + settings.scaleDownThreshold);
		}

		if(playersPerActiveInstance >= settings.scaleUpThreshold) {
			if (DEBUG_SCALING) {
				System.out.println("Decision: SCALE UP (" + String.format("%.2f", playersPerActiveInstance) + " >= " + settings.scaleUpThreshold + ")");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.UP;
		} else if(startingInstances > 0) {
			// Don't scale down while instances are still starting - wait for them to become RUNNING
			if (DEBUG_SCALING) {
				System.out.println("Decision: NO CHANGE (waiting for " + startingInstances + " starting instances)");
				System.out.println("--- End Threshold Check ---");
			}
			return ScaleResult.NO_CHANGE;
		} else if(playersPerRunningInstance < settings.scaleDownThreshold) {
			if (DEBUG_SCALING) {
				System.out.println("Decision: SCALE DOWN (" + String.format("%.2f", playersPerRunningInstance) + " < " + settings.scaleDownThreshold + ")");
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

	private ScaleResult checkToScaleTrend(GameServerWrapper<MinecraftInstance> gameServerWrapper) {
		// Trend-based scaling not yet implemented
		return ScaleResult.NO_CHANGE;
	}

	private int calculateTargetReplicas(GameServerWrapper<MinecraftInstance> gameServerWrapper, ScaleResult result) {
		int activeCurrentInstances = getActiveInstanceCount(gameServerWrapper);
		int playerCount = getPlayerCount(gameServerWrapper);
		int instancesToAdd = 0;

		ScalingSettings settings = gameServerWrapper.getScalingSettings();

		if (DEBUG_SCALING) {
			System.out.println("\n--- Calculating Target Replicas ---");
		}

		if(result == ScaleResult.UP) {
			int scaleUpLimit = settings.scaleUpLimit;
			double targetRatio = settings.scaleUpThreshold;

			// Check if we're below minimum instances
			if(activeCurrentInstances < settings.minInstances) {
				// Add instances to reach minimum (bypass scaleUpLimit - recovery is priority)
				int needed = settings.minInstances - activeCurrentInstances;
				instancesToAdd = needed;
				if (DEBUG_SCALING) {
					System.out.println("Scale-up calculation (below minimum - bypassing limits):");
					System.out.println("  Current active: " + activeCurrentInstances);
					System.out.println("  Minimum required: " + settings.minInstances);
					System.out.println("  Adding: " + instancesToAdd + " (scaleUpLimit bypassed)");
				}
			} else {
				// At or above minimum, use ratio-based scaling
				double playersPerInstance = (double) playerCount / activeCurrentInstances;

				if (DEBUG_SCALING) {
					System.out.println("Scale-up calculation (ratio-based):");
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

		// Calculate target replicas = total non-terminating instances + instances to add/remove
		// instancesToAdd is based on ACTIVE (RUNNING+STARTING) instances, but we add to total count
		// This ensures we account for BLOCKED/STARTING instances (but NOT STOPPING/STOPPED)
		int targetReplicas = getTotalNonTerminatingInstanceCount(gameServerWrapper) + instancesToAdd;

		// CRITICAL SAFETY CHECK: Never allow target to go below minInstances
		// This is a defensive check in case the logic above miscalculates
		if (targetReplicas < settings.minInstances) {
			if (DEBUG_SCALING) {
				System.out.println("WARNING: Calculated target (" + targetReplicas + ") is below minInstances (" + settings.minInstances + ")");
				System.out.println("Enforcing minInstances as target");
			}
			targetReplicas = settings.minInstances;
		}

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

		// Filter to only include instances that are safe to remove
		// CRITICAL: Exclude BLOCKED instances (already pending shutdown from previous scaling decision)
		// Only select RUNNING instances
		List<MinecraftInstance> candidates = instances.stream()
			.filter(instance -> instance.getState() == InstanceState.RUNNING)
			.toList();

		if (DEBUG_SCALING) {
			System.out.println("  Total instances: " + instances.size());
			System.out.println("  RUNNING candidates: " + candidates.size());
			candidates.forEach(instance ->
				System.out.println("    Candidate: " + instance.getName() + ", Players: " + instance.getPlayers().size() + ", State: " + instance.getState())
			);
		}

		// Sort by player count (ascending) - instances with fewest players first
		List<MinecraftInstance> sortedCandidates = new ArrayList<>(candidates);
		sortedCandidates.sort(Comparator.comparingInt(instance -> instance.getPlayers().size()));

		// Take the first N candidates (those with fewest players)
		int toRemove = Math.min(count, sortedCandidates.size());

		if (DEBUG_SCALING) {
			System.out.println("Selected " + toRemove + " instances for removal:");
			sortedCandidates.subList(0, toRemove).forEach(instance ->
				System.out.println("    -> " + instance.getName() + " (" + instance.getPodName() + "), Players: " + instance.getPlayers().size())
			);
			System.out.println("--- End Selecting Pods ---");
		}

		return new ArrayList<>(sortedCandidates.subList(0, toRemove));
	}

	private int getPlayerCount(GameServerWrapper<MinecraftInstance> gameServerWrapper) {
		List<MinecraftInstance> instances = gameServerWrapper.getInstances();
		return instances.stream().mapToInt(instance -> instance.getPlayers().size()).sum();
	}

	private int getActiveInstanceCount(GameServerWrapper<? extends Instance> gameServerWrapper) {
		// Count RUNNING and STARTING instances as "active" for minimum instance checks
		// STARTING instances are provisioned capacity that will soon be RUNNING
		// BLOCKED, STOPPING, and STOPPED instances don't count
		return (int) gameServerWrapper.getInstances().stream()
			.filter(instance ->
				instance.getState() == InstanceState.RUNNING ||
				instance.getState() == InstanceState.STARTING)
			.count();
	}

	private int getRunningInstanceCount(GameServerWrapper<? extends Instance> gameServerWrapper) {
		// Count only RUNNING instances - used for scale-down decisions
		// This ensures we don't scale down while new capacity is still starting
		return (int) gameServerWrapper.getInstances().stream()
			.filter(instance -> instance.getState() == InstanceState.RUNNING)
			.count();
	}

	private int getTotalNonTerminatingInstanceCount(GameServerWrapper<? extends Instance> gameServerWrapper) {
		// Count all instances EXCEPT those that are shutting down or already accepted shutdown
		// DRAINING = shutdown accepted by manager, going away soon (like STOPPING)
		// BLOCKED = game in progress set by server, still real active capacity - included
		return (int) gameServerWrapper.getInstances().stream()
			.filter(instance ->
				instance.getState() != InstanceState.DRAINING &&
				instance.getState() != InstanceState.STOPPING &&
				instance.getState() != InstanceState.STOPPED)
			.count();
	}

	private boolean hasDrainingInstances(GameServerWrapper<? extends Instance> gameServerWrapper) {
		return gameServerWrapper.getInstances().stream()
			.anyMatch(instance -> instance.getState() == InstanceState.DRAINING);
	}
}
