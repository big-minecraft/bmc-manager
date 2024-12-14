package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.ProxyManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.Proxy;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.Objects;

public class ProxyDiscoveryTask {
	private final KubernetesClient client;

	public ProxyDiscoveryTask() {
		this.client = new KubernetesClientBuilder().build();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					updateProxy();
				}
			}, RedisChannel.PROXY_MODIFIED.getRef());
		}).start();

		new Thread(() -> {
			//Sleep to ensure that ServerDiscoveryTask has had time to register all instances
			try { Thread.sleep(5000); } catch(InterruptedException e) { throw new RuntimeException(e); }
			updateProxy();
		}).start();
	}

	public void updateProxy() {
		ProxyManager proxyManager = BMCManager.proxyManager;

		List<io.fabric8.kubernetes.api.model.apps.Deployment> deployments = client.apps().deployments()
				.inNamespace("default")
				.list()
				.getItems()
				.stream()
				.filter(deployment -> Objects.equals(deployment.getMetadata().getName(), "proxy"))
				.toList();

		if(deployments.size() != 1) {
			throw new RuntimeException("Expected 1 proxy deployment, found " + deployments.size());
		}

		Deployment deployment = deployments.getFirst();
		Proxy proxy = new Proxy(deployment);

		proxy.fetchInstances();
		proxyManager.updateProxy(proxy);
	}
}