package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.interfaces.Scalable;
import dev.wiji.bigminecraftapi.BigMinecraftAPI;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Deployment implements Scalable {
	private final String name;
	private final boolean isInitial;
	private final QueueStrategy queueStrategy;

	private final ScalingSettings scalingSettings;

	private final List<MinecraftInstance> instances;

	private long lastScaleUp = 0;
	private long lastScaleDown = 0;

	public Deployment(io.fabric8.kubernetes.api.model.apps.Deployment deployment) {
		this.name = deployment.getMetadata().getName();

		this.isInitial = Boolean.parseBoolean(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.INITIAL_SERVER.getLabel()));

		this.queueStrategy = QueueStrategy.getStrategy(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.QUEUE_STRATEGY.getLabel()));

		this.scalingSettings = new ScalingSettings(deployment.getSpec().getTemplate().getMetadata().getLabels());

		this.instances = new ArrayList<>();
	}

	public void fetchInstances() {
		instances.clear();

		for (MinecraftInstance instance : BigMinecraftAPI.getNetworkManager().getInstances()) {
			if (instance.getDeployment().equals(name)) instances.add(instance);
		}
	}

	public void scale() {
		ScaleResult result = BMCManager.scalingManager.checkToScale(this);
		BMCManager.scalingManager.scale(this, result);
	}

	public boolean isInitial() {
		return isInitial;
	}

	public QueueStrategy getQueueStrategy() {
		return queueStrategy;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ScalingSettings getScalingSettings() {
		return scalingSettings;
	}

	@Override
	public List<MinecraftInstance> getInstances() {
		return new ArrayList<>(instances);
	}

	@Override
	public boolean isOnScaleUpCooldown() {
		return System.currentTimeMillis() - lastScaleUp < scalingSettings.scaleUpCooldown * 1000;
	}

	@Override
	public boolean isOnScaleDownCooldown() {
		return System.currentTimeMillis() - lastScaleDown < scalingSettings.scaleDownCooldown * 1000;
	}

	@Override
	public void setLastScaleUp(long lastScaleUp) {
		this.lastScaleUp = lastScaleUp;
	}

	@Override
	public void setLastScaleDown(long lastScaleDown) {
		this.lastScaleDown = lastScaleDown;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Deployment deployment = (Deployment) o;
		return isInitial == deployment.isInitial &&
				Objects.equals(name, deployment.name) &&
				queueStrategy == deployment.queueStrategy &&
				Objects.equals(scalingSettings, deployment.scalingSettings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, isInitial, queueStrategy, scalingSettings);
	}
}
