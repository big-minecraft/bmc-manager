package dev.kyriji.bmcmanager.k8s;

import dev.kyriji.bmcmanager.redis.RedisManager;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ServerDiscovery {
	private final KubernetesClient client;
	private static final String LABEL = "kyriji.dev/enable-server-discovery";

	private final HashMap<String, Pod> podMap = new HashMap<>();
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


	public ServerDiscovery() {
		client = new KubernetesClientBuilder().build();

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

	public void discoverServers() {
		List<Pod> podList = client.pods().withLabel(LABEL, "true").list().getItems();

		podList.forEach(pod -> {
			if (diff(pod)) RedisManager.registerInstance(pod);
		});


		List<String> uidList = getDeletedPodUidList(client);
		for(String uid : uidList) RedisManager.unregisterInstance(uid);
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

		for (String uid : uidList) {
			podMap.remove(uid);
		}

		return uidList;
	}
}
