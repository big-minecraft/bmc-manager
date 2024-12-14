package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.wiji.bigminecraftapi.BigMinecraftAPI;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Deployment {

	private final String name;
	private final boolean isInitial;
	private final QueueStrategy queueStrategy;
	private final int maxPlayers;

	private final List<MinecraftInstance> instances;

	public Deployment(io.fabric8.kubernetes.api.model.apps.Deployment deployment) {
		this.name = deployment.getMetadata().getName();

		this.isInitial = Boolean.parseBoolean(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.INITIAL_SERVER.getLabel()));

		this.queueStrategy = QueueStrategy.getStrategy(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.QUEUE_STRATEGY.getLabel()));

		this.maxPlayers = Integer.parseInt(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.MAX_PLAYERS.getLabel()));

		this.instances = new ArrayList<>();
	}

	public void fetchInstances() {
		instances.clear();

		for (MinecraftInstance instance : BigMinecraftAPI.getNetworkManager().getInstances()) {
			if (instance.getGamemode().equals(name)) instances.add(instance);
		}
	}

	public String getName() {
		return name;
	}

	public boolean isInitial() {
		return isInitial;
	}

	public QueueStrategy getQueueStrategy() {
		return queueStrategy;
	}

	public List<MinecraftInstance> getInstances() {
		return instances;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Deployment deployment = (Deployment) o;
		return Objects.equals(name, deployment.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}
}
