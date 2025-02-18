package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.factories.InstanceFactory;
import dev.kyriji.bmcmanager.controllers.InstanceManager;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
import dev.wiji.bigminecraftapi.objects.Instance;
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
				discoverInstances();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private void discoverInstances() {
		List<Pod> podList = client.pods().withLabel(DeploymentLabel.SERVER_DISCOVERY.getLabel(), "true").list().getItems();
		List<Pod> proxyList = client.pods().withLabel("app", "proxy").list().getItems();

		podList.addAll(proxyList);
		System.out.println("Pod list size: " + podList.size());

		podList.forEach(pod -> {
			if (pod.getStatus().getPodIP() == null) return;
			if (pod.getStatus().getPhase().equals("Terminating")) return;

			System.out.println("New pod detected: " + pod.getMetadata().getName());

			if (diff(pod)) {
				Instance instance = InstanceFactory.createFromPod(pod);
				if (instance == null) return;
				System.out.println("Instance created: " + instance.getName());

				instanceManager.registerInstance(instance);
			}
		});

		Map<String, Pod> uidList = getDeletedPodList(client);
		uidList.forEach((key, value) -> {
			String deploymentName = value.getMetadata().getLabels().get("app");
			String uid = value.getMetadata().getUid();

			instanceManager.unregisterInstance(deploymentName, uid);
		});

		BMCManager.deploymentManager.getDeployments().forEach(DeploymentWrapper::fetchInstances);
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