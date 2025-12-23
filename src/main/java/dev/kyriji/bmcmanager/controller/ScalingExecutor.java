package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.kyriji.bmcmanager.objects.ScalingDecision;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;

public class ScalingExecutor {
	// ============ DEBUG FLAG ============
	// Set to false to disable all scaling debug output
	private static final boolean DEBUG_SCALING = true;
	// ====================================

	private final KubernetesClient client;

	public ScalingExecutor(KubernetesClient client) {
		this.client = client;
	}

	public void executeScaling(ScalingDecision decision, HasMetadata resource, DeploymentWrapper<?> wrapper) {
		if (decision.getAction() == ScaleResult.NO_CHANGE) {
			return;
		}

		String namespace = resource.getMetadata().getNamespace();
		String name = resource.getMetadata().getName();

		if (DEBUG_SCALING) {
			System.out.println("\n========== EXECUTING SCALING ==========");
			System.out.println("Resource: " + namespace + "/" + name);
			System.out.println("Action: " + decision.getAction());
			System.out.println("Current replicas: " + decision.getCurrentReplicas());
			System.out.println("Target replicas: " + decision.getTargetReplicas());
		}

		if (decision.getAction() == ScaleResult.UP) {
			scaleUp(decision, resource, namespace, name, wrapper);
		} else if (decision.getAction() == ScaleResult.DOWN) {
			scaleDown(decision, resource, namespace, name, wrapper);
		}
	}

	private void scaleUp(ScalingDecision decision, HasMetadata resource, String namespace, String name, DeploymentWrapper<?> wrapper) {
		if (DEBUG_SCALING) {
			System.out.println("\n--- Scale-Up Execution ---");
			System.out.println("Updating replica count to: " + decision.getTargetReplicas());
		}

		// For scale-up, simply update the replica count
		// Kubernetes will automatically create new pods
		updateReplicas(resource, namespace, name, decision.getTargetReplicas());

		if (DEBUG_SCALING) {
			System.out.println("Replica count updated successfully");
			System.out.println("Kubernetes will automatically create " + (decision.getTargetReplicas() - decision.getCurrentReplicas()) + " new pod(s)");
			System.out.println("Setting scale-up cooldown");
			System.out.println("--- End Scale-Up Execution ---");
			System.out.println("========== SCALING EXECUTION COMPLETE ==========\n");
		}

		// Update cooldown timestamp
		wrapper.setLastScaleUp(System.currentTimeMillis());
	}

	private void scaleDown(ScalingDecision decision, HasMetadata resource, String namespace, String name, DeploymentWrapper<?> wrapper) {
		// CRITICAL FIX: Update replicas BEFORE deleting pods to avoid race condition

		if (DEBUG_SCALING) {
			System.out.println("\n--- Scale-Down Execution ---");
			System.out.println("Pods to delete: " + decision.getPodsToDelete().size());
		}

		// Step 1: Mark instances as draining in Redis (prevents new players from joining)
		if (DEBUG_SCALING) {
			System.out.println("\nStep 1: Marking instances as STOPPING in Redis");
		}
		for (Instance instance : decision.getPodsToDelete()) {
			if (DEBUG_SCALING) {
				System.out.println("  Marking " + instance.getPodName() + " as STOPPING");
			}
			instance.setState(InstanceState.STOPPING);
			RedisManager.get().updateInstance(instance);
		}

		// Step 2: Update replica count FIRST
		// This tells Kubernetes the new desired state before we delete any pods
		// This prevents Kubernetes from recreating the pods we're about to delete
		if (DEBUG_SCALING) {
			System.out.println("\nStep 2: Updating replica count to " + decision.getTargetReplicas());
		}
		updateReplicas(resource, namespace, name, decision.getTargetReplicas());
		if (DEBUG_SCALING) {
			System.out.println("  Replica count updated successfully");
		}

		// Step 3: Delete specific pods
		// Since we already updated the desired replica count, Kubernetes won't recreate these pods
		if (DEBUG_SCALING) {
			System.out.println("\nStep 3: Deleting specific pods");
		}
		for (Instance instance : decision.getPodsToDelete()) {
			try {
				if (DEBUG_SCALING) {
					System.out.println("  Deleting pod: " + instance.getPodName());
				}
				client.pods()
					.inNamespace(namespace)
					.withName(instance.getPodName())
					.delete();
				if (DEBUG_SCALING) {
					System.out.println("    Deleted successfully");
				}
			} catch (Exception e) {
				// Log error but continue - Kubernetes will eventually reconcile
				System.err.println("Failed to delete pod " + instance.getPodName() + ": " + e.getMessage());
			}
		}

		// Step 4: Update cooldown timestamp
		if (DEBUG_SCALING) {
			System.out.println("\nStep 4: Setting scale-down cooldown");
			System.out.println("--- End Scale-Down Execution ---");
			System.out.println("========== SCALING EXECUTION COMPLETE ==========\n");
		}
		wrapper.setLastScaleDown(System.currentTimeMillis());
	}

	private void updateReplicas(HasMetadata resource, String namespace, String name, int replicas) {
		if (resource instanceof Deployment deployment) {
			deployment.getSpec().setReplicas(replicas);
			client.apps().deployments()
				.inNamespace(namespace)
				.resource(deployment)
				.update();
		} else if (resource instanceof StatefulSet statefulSet) {
			statefulSet.getSpec().setReplicas(replicas);
			client.apps().statefulSets()
				.inNamespace(namespace)
				.resource(statefulSet)
				.update();
		}
	}

	public int getCurrentReplicas(HasMetadata resource) {
		if (resource instanceof Deployment deployment) {
			return deployment.getSpec().getReplicas();
		} else if (resource instanceof StatefulSet statefulSet) {
			return statefulSet.getSpec().getReplicas();
		}
		return 0;
	}
}
