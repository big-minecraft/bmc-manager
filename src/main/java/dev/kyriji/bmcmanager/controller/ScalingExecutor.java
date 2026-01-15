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

		if (decision.getAction() == ScaleResult.UP) {
			scaleUp(decision, resource, namespace, name, wrapper);
		} else if (decision.getAction() == ScaleResult.DOWN) {
			scaleDown(decision, resource, namespace, name, wrapper);
		}
	}

	private void scaleUp(ScalingDecision decision, HasMetadata resource, String namespace, String name, DeploymentWrapper<?> wrapper) {
		// For scale-up, simply update the replica count
		// Kubernetes will automatically create new pods
		updateReplicas(resource, namespace, name, decision.getTargetReplicas());

		// Update cooldown timestamp
		wrapper.setLastScaleUp(System.currentTimeMillis());
	}

	private void scaleDown(ScalingDecision decision, HasMetadata resource, String namespace, String name, DeploymentWrapper<?> wrapper) {
		// CRITICAL: We must manually delete specific pods to control WHICH instances are removed
		// (e.g., those with fewest players). If we just update replicas, Kubernetes chooses randomly.

		// Step 1: Mark instances as STOPPING in Redis (prevents new players from joining)
		for (Instance instance : decision.getPodsToDelete()) {
			instance.setState(InstanceState.STOPPING);
			RedisManager.get().updateInstance(instance);
		}

		// Step 2: Manually delete the specific pods we selected
		// This ensures we remove instances with fewest players
		for (Instance instance : decision.getPodsToDelete()) {
			try {
				client.pods()
					.inNamespace(namespace)
					.withName(instance.getPodName())
					.delete();
				System.out.println("Deleted pod: " + instance.getPodName() + " (selected for scale-down)");
			} catch (Exception e) {
				System.err.println("Failed to delete pod " + instance.getPodName() + ": " + e.getMessage());
			}
		}

		// Step 3: Update replica count AFTER deleting pods
		// This prevents Kubernetes from creating replacements for the pods we just deleted
		updateReplicas(resource, namespace, name, decision.getTargetReplicas());

		// Step 4: Update cooldown timestamp
		wrapper.setLastScaleDown(System.currentTimeMillis());
	}

	private void updateReplicas(HasMetadata resource, String namespace, String name, int replicas) {
		if (resource instanceof Deployment deployment) {
			deployment.getSpec().setReplicas(replicas);
			client.apps().deployments()
				.inNamespace(namespace)
				.resource(deployment)
				.update();
		}
		// StatefulSet scaling removed - only Deployments are scaled
	}

	public int getCurrentReplicas(HasMetadata resource) {
		if (resource instanceof Deployment deployment) {
			return deployment.getSpec().getReplicas();
		}
		// StatefulSet scaling removed - only Deployments are scaled
		return 0;
	}
}
