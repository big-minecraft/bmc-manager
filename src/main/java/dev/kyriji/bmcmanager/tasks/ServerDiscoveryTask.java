package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.factories.MinecraftInstanceFactory;
import dev.kyriji.bmcmanager.controllers.NetworkInstanceManager;
import dev.kyriji.bmcmanager.objects.Deployment;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ServerDiscoveryTask {
	private final KubernetesClient client;
	private final NetworkInstanceManager networkInstanceManager;
	private final HashMap<String, Pod> podMap = new HashMap<>();

	public ServerDiscoveryTask(NetworkInstanceManager networkInstanceManager) {
		this.networkInstanceManager = networkInstanceManager;
		this.client = new KubernetesClientBuilder().build();

		new Thread(() -> {
			while (true) {
				discoverServers();
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private void discoverServers() {
		List<Pod> podList = client.pods().withLabel(DeploymentLabel.SERVER_DISCOVERY.getLabel(), "true").list().getItems();
		List<Pod> proxyList = client.pods().withLabel("app", "proxy").list().getItems();

		podList.addAll(proxyList);

		podList.forEach(pod -> {
			if (pod.getStatus().getPodIP() == null) return;
			if (pod.getStatus().getPhase().equals("Terminating")) return;

			boolean isProxy = pod.getMetadata().getLabels().get("app").equals("proxy");

			if (diff(pod)) {
				MinecraftInstance instance = MinecraftInstanceFactory.createFromPod(pod);
				if (isProxy) {
					networkInstanceManager.registerProxy(instance);
				} else {
					networkInstanceManager.registerInstance(instance);
				}
			}
		});

		List<String> uidList = getDeletedPodUidList(client);
		for (String uid : uidList) {
			networkInstanceManager.unregisterInstance(uid);
		}

		BMCManager.deploymentManager.getDeployments().forEach(Deployment::fetchInstances);
		if(BMCManager.proxyManager.proxyDeployment != null) BMCManager.proxyManager.getProxyDeployment().fetchInstances();
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

	private List<String> getDeletedPodUidList(KubernetesClient client) {
		List<String> uidList = new ArrayList<>(podMap.keySet());
		client.pods().list().getItems().forEach(pod -> uidList.remove(pod.getMetadata().getUid()));
		for (String uid : uidList) podMap.remove(uid);
		return uidList;
	}
}