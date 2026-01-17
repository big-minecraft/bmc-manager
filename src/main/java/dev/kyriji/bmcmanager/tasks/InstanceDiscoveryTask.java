package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.factories.InstanceFactory;
import dev.kyriji.bmcmanager.controllers.InstanceManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import dev.kyriji.bigminecraftapi.objects.Instance;
import io.fabric8.kubernetes.api.model.Pod;
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
}
