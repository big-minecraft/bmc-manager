package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.factories.InstanceFactory;
import dev.kyriji.bmcmanager.controllers.InstanceManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.enums.InstanceState;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.*;

public class InstanceDiscoveryTask {
	private final KubernetesClient client;
	private final InstanceManager instanceManager;
	private final HashMap<String, Pod> podMap = new HashMap<>();

	public InstanceDiscoveryTask(InstanceManager instanceManager) {
		this.instanceManager = instanceManager;
		this.client = new KubernetesClientBuilder().build();

		new Thread(() -> {
			while (true) {
				try {
					discoverInstances();
					Thread.sleep(5000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private void discoverInstances() {
		// Find all pods managed by bmc-manager (created by PodBuilder with this label)
		List<Pod> podList = client.pods().withLabel("kyriji.dev/managed-by", "bmc-manager").list().getItems();
		// Also include proxy pods (for backwards compatibility or standalone proxies)
		List<Pod> proxyList = client.pods().withLabel("app", "proxy").list().getItems();

		podList.addAll(proxyList);

		podList.forEach(pod -> {
			if (pod.getStatus().getPodIP() == null) return;
			if (pod.getStatus().getPhase().equals("Terminating")) return;
			// Skip pods that are being deleted (deletion timestamp is set before phase changes to Terminating)
			if (pod.getMetadata().getDeletionTimestamp() != null) return;

			// Check for failed pods and handle them
			if (isPodFailed(pod)) {
				handleFailedPod(pod);
				return; // Don't register failed pods
			}

			if (diff(pod)) {
				Instance instance = InstanceFactory.createFromPod(pod);
				if (instance == null) return;

				instanceManager.registerInstance(instance);
			}
		});

		Map<String, Pod> uidList = getDeletedPodList(client);
		uidList.forEach((key, value) -> {
			String deploymentName = value.getMetadata().getLabels().get("app");
			String uid = value.getMetadata().getUid();
			String podName = value.getMetadata().getName();

			System.out.println("Pod no longer exists in K8s, unregistering: " + podName + " (uid=" + uid + ", deployment=" + deploymentName + ")");
			instanceManager.unregisterInstance(deploymentName, uid);
		});

		BMCManager.gameServerManager.getGameServers().forEach(GameServerWrapper::fetchInstances);

		RedisManager.get().updateTimestamp();
	}

	private boolean diff(Pod pod) {
		String uid = pod.getMetadata().getUid();
		if (!podMap.containsKey(uid)) {
			podMap.put(uid, pod);
			return true;
		}

		Pod oldPod = podMap.get(uid);
		if (!Objects.equals(oldPod.getStatus().getPhase(), pod.getStatus().getPhase())) {
			podMap.put(uid, pod);
			return true;
		}

		return false;
	}

	private Map<String, Pod> getDeletedPodList(KubernetesClient client) {
		Map<String, Pod> removedMap = new HashMap<>(podMap);
		client.pods().list().getItems().forEach(pod -> removedMap.remove(pod.getMetadata().getUid()));
		for (String uid : removedMap.keySet()) podMap.remove(uid);
		return removedMap;
	}

	private boolean isPodFailed(Pod pod) {
		String phase = pod.getStatus().getPhase();

		// Check for Unknown or Failed phase
		if ("Unknown".equals(phase) || "Failed".equals(phase)) {
			return true;
		}

		// Check for OOMKilled or Error containers
		List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
		if (containerStatuses != null) {
			for (ContainerStatus status : containerStatuses) {
				// Check current state
				if (status.getState() != null && status.getState().getTerminated() != null) {
					String reason = status.getState().getTerminated().getReason();
					if ("OOMKilled".equals(reason) || "Error".equals(reason)) {
						return true;
					}
				}

				// Check last state (for restarting containers)
				if (status.getLastState() != null && status.getLastState().getTerminated() != null) {
					String reason = status.getLastState().getTerminated().getReason();
					if ("OOMKilled".equals(reason)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private String getFailureReason(Pod pod) {
		String phase = pod.getStatus().getPhase();
		if ("Unknown".equals(phase)) return "Unknown phase";
		if ("Failed".equals(phase)) return "Failed phase";

		List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
		if (containerStatuses != null) {
			for (ContainerStatus status : containerStatuses) {
				if (status.getState() != null && status.getState().getTerminated() != null) {
					String reason = status.getState().getTerminated().getReason();
					if (reason != null) return "Container terminated: " + reason;
				}
				if (status.getLastState() != null && status.getLastState().getTerminated() != null) {
					String reason = status.getLastState().getTerminated().getReason();
					if (reason != null) return "Container last state: " + reason;
				}
			}
		}

		return "Unknown failure";
	}

	private void handleFailedPod(Pod pod) {
		String podName = pod.getMetadata().getName();
		String uid = pod.getMetadata().getUid();
		String namespace = pod.getMetadata().getNamespace();
		String deployment = pod.getMetadata().getLabels().get("app");
		String reason = getFailureReason(pod);

		System.out.println("Detected failed pod: " + podName +
			" (reason=" + reason + ", uid=" + uid + ", deployment=" + deployment + ")");

		// Try to mark instance as STOPPING in Redis
		Instance instance = instanceManager.getInstances().stream()
			.filter(i -> i.getUid().equals(uid))
			.findFirst()
			.orElse(null);

		if (instance != null) {
			instance.setState(InstanceState.STOPPING);
			RedisManager.get().updateInstance(instance);
			System.out.println("Marked failed pod as STOPPING in Redis: " + podName);
		}

		// Delete the pod
		try {
			client.pods()
				.inNamespace(namespace != null ? namespace : "default")
				.withName(podName)
				.delete();
			System.out.println("Deleted failed pod: " + podName + " (reason=" + reason + ")");
		} catch (Exception e) {
			System.err.println("Failed to delete failed pod " + podName + ": " + e.getMessage());
		}
	}
}
