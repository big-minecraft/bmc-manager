package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.ResourceType;
import dev.kyriji.bmcmanager.logic.ScalingLogic;
import dev.kyriji.bmcmanager.objects.*;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.lang.reflect.Type;
import java.util.Map;

public class DeploymentReconciler {
	private final KubernetesClient client;
	private final ScalingLogic scalingLogic;
	private final ScalingExecutor scalingExecutor;

	public DeploymentReconciler(KubernetesClient client) {
		this.client = client;
		this.scalingLogic = new ScalingLogic();
		this.scalingExecutor = new ScalingExecutor(client);
	}

	public ReconcileResult reconcile(ReconcileRequest request) {
		try {
			// 1. Fetch resource from Kubernetes
			HasMetadata resource = fetchResource(request);
			if (resource == null) {
				// Resource not found, don't requeue
				return ReconcileResult.noRequeue();
			}

			// 2. Check if it should be autoscaled
			if (!shouldAutoscale(resource)) {
				// Not enabled for autoscaling, don't requeue
				return ReconcileResult.noRequeue();
			}

			// 3. Check if replicas are set to 0 (deployment is disabled)
			int currentReplicas = scalingExecutor.getCurrentReplicas(resource);
			if (currentReplicas == 0) {
				// Deployment is disabled, don't scale
				return ReconcileResult.noRequeue();
			}

			// 4. Get the DeploymentWrapper from the registry
			DeploymentWrapper<?> wrapper = BMCManager.deploymentManager.getDeployment(request.getName());
			if (wrapper == null) {
				// Not registered yet, requeue to try again later
				return ReconcileResult.requeueAfter(5000);
			}

			// 5. Only handle MinecraftInstance deployments for now
			Type instanceType = wrapper.getInstanceType();

			// Check if the type is MinecraftInstance.class
			if (!MinecraftInstance.class.equals(instanceType)) {
				return ReconcileResult.noRequeue();
			}

			@SuppressWarnings("unchecked")
			DeploymentWrapper<MinecraftInstance> minecraftWrapper = (DeploymentWrapper<MinecraftInstance>) wrapper;

			// 6. Fetch latest instance data from Redis
			minecraftWrapper.fetchInstances();

			// 7. Determine scaling action
			ScalingDecision decision = scalingLogic.determineScalingAction(minecraftWrapper);

			// 8. Execute scaling if needed
			if (decision.getAction() != dev.kyriji.bmcmanager.enums.ScaleResult.NO_CHANGE) {
				scalingExecutor.executeScaling(decision, resource, wrapper);
				System.out.println("Scaled " + request.getName() + ": " + decision);
			}

			// 9. Always requeue after 5 seconds for periodic checks
			return ReconcileResult.requeueAfter(5000);

		} catch (Exception e) {
			// Log error and requeue after longer delay
			System.err.println("Error reconciling " + request + ": " + e.getMessage());
			e.printStackTrace();
			return ReconcileResult.requeueAfter(10000);
		}
	}

	private HasMetadata fetchResource(ReconcileRequest request) {
		try {
			if (request.getResourceType() == ResourceType.DEPLOYMENT) {
				return client.apps().deployments()
					.inNamespace(request.getNamespace())
					.withName(request.getName())
					.get();
			} else if (request.getResourceType() == ResourceType.STATEFULSET) {
				return client.apps().statefulSets()
					.inNamespace(request.getNamespace())
					.withName(request.getName())
					.get();
			}
		} catch (Exception e) {
			System.err.println("Failed to fetch resource " + request + ": " + e.getMessage());
		}
		return null;
	}

	private boolean shouldAutoscale(HasMetadata resource) {
		// Check deployment/statefulset metadata labels first
		Map<String, String> labels = resource.getMetadata().getLabels();
		if (labels != null) {
			String serverDiscovery = labels.get(DeploymentLabel.SERVER_DISCOVERY.getLabel());
			if ("true".equals(serverDiscovery)) {
				return true;
			}
		}

		// Also check spec.template.metadata.labels (pod template labels)
		Map<String, String> templateLabels = null;
		if (resource instanceof Deployment deployment) {
			templateLabels = deployment.getSpec().getTemplate().getMetadata().getLabels();
		} else if (resource instanceof StatefulSet statefulSet) {
			templateLabels = statefulSet.getSpec().getTemplate().getMetadata().getLabels();
		}

		if (templateLabels != null) {
			String serverDiscovery = templateLabels.get(DeploymentLabel.SERVER_DISCOVERY.getLabel());
			return "true".equals(serverDiscovery);
		}

		return false;
	}
}
