package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import io.fabric8.kubernetes.api.model.PodBuilder;

public class PodLabelManager {

	public static final String LB_READY_LABEL = "kyriji.dev/lb-ready";

	/**
	 * Syncs the kyriji.dev/lb-ready label on the pod based on the instance's current state.
	 * The label is present only when state == RUNNING, ensuring the LoadBalancer only
	 * routes traffic to healthy, non-draining instances.
	 *
	 * Only applies to proxy deployment types.
	 */
	public static void syncLbLabel(Instance instance) {
		if (instance == null || instance.getPodName() == null) return;

		GameServerWrapper<?> wrapper = BMCManager.gameServerManager.getGameServer(instance.getDeployment());
		if (wrapper == null || !"proxy".equalsIgnoreCase(wrapper.getDeploymentType())) return;

		boolean shouldBeReady = instance.getState() == InstanceState.RUNNING;

		try {
			if (shouldBeReady) {
				BMCManager.kubernetesClient.pods()
						.inNamespace("default")
						.withName(instance.getPodName())
						.edit(pod -> new PodBuilder(pod)
								.editMetadata()
									.addToLabels(LB_READY_LABEL, "true")
								.endMetadata()
								.build());
				System.out.println("Added lb-ready label to proxy pod: " + instance.getPodName());
			} else {
				BMCManager.kubernetesClient.pods()
						.inNamespace("default")
						.withName(instance.getPodName())
						.edit(pod -> new PodBuilder(pod)
								.editMetadata()
									.removeFromLabels(LB_READY_LABEL)
								.endMetadata()
								.build());
				System.out.println("Removed lb-ready label from proxy pod: " + instance.getPodName());
			}
		} catch (Exception e) {
			System.err.println("Failed to sync lb-ready label for pod " + instance.getPodName() + ": " + e.getMessage());
		}
	}
}
