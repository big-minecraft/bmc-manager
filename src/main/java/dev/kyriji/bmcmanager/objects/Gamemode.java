package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;
import dev.wiji.bigminecraftapi.BigMinecraftAPI;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Gamemode {
	private final String name;
	private final boolean isInitial;
	private final QueueStrategy queueStrategy;

	private final ScalingSettings scalingSettings;

	private final List<MinecraftInstance> instances;

	private long lastScaleUp = 0;
	private long lastScaleDown = 0;

	public Gamemode(Deployment deployment) {
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
			if (instance.getGamemode().equals(name)) instances.add(instance);
		}
	}

	public void scale() {
		ScaleResult result = BMCManager.scalingManager.checkToScale(this);
		BMCManager.scalingManager.scale(this, result);
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
		return new ArrayList<>(instances);
	}

	public ScalingSettings getScalingSettings() {
		return scalingSettings;
	}

	public boolean isOnScaleUpCooldown() {
		return System.currentTimeMillis() - lastScaleUp < scalingSettings.scaleUpCooldown * 1000;
	}

	public boolean isOnScaleDownCooldown() {
		return System.currentTimeMillis() - lastScaleDown < scalingSettings.scaleDownCooldown * 1000;
	}

	public void setLastScaleUp(long lastScaleUp) {
		this.lastScaleUp = lastScaleUp;
	}

	public void setLastScaleDown(long lastScaleDown) {
		this.lastScaleDown = lastScaleDown;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Gamemode gamemode = (Gamemode) o;
		return isInitial == gamemode.isInitial &&
				Objects.equals(name, gamemode.name) &&
				queueStrategy == gamemode.queueStrategy &&
				Objects.equals(scalingSettings, gamemode.scalingSettings);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, isInitial, queueStrategy, scalingSettings);
	}

	public static class ScalingSettings {
		public ScaleStrategy strategy;

		public int maxPlayers;
		public int minInstances;
		public int maxInstances;

		public double scaleUpThreshold;
		public double scaleDownThreshold;

		public double scaleUpCooldown;
		public double scaleDownCooldown;

		public int scaleUpLimit;
		public int scaleDownLimit;

		public ScalingSettings(Map<String, String> labels) {
			this.strategy = ScaleStrategy.getStrategy(labels.get(DeploymentLabel.SCALE_STRATEGY.getLabel()));

			this.maxPlayers = Integer.parseInt(labels.get(DeploymentLabel.MAX_PLAYERS.getLabel()));
			this.minInstances = Integer.parseInt(labels.get(DeploymentLabel.MIN_INSTANCES.getLabel()));

			String maxInstancesString = labels.get(DeploymentLabel.MAX_INSTANCES.getLabel());
			if(maxInstancesString.equalsIgnoreCase("unlimited")) this.maxInstances = Integer.MAX_VALUE;
			else this.maxInstances = Integer.parseInt(labels.get(DeploymentLabel.MAX_INSTANCES.getLabel()));

			this.scaleUpThreshold = Double.parseDouble(labels.get(DeploymentLabel.SCALE_UP_THRESHOLD.getLabel()));
			this.scaleDownThreshold = Double.parseDouble(labels.get(DeploymentLabel.SCALE_DOWN_THRESHOLD.getLabel()));

			this.scaleUpCooldown = Double.parseDouble(labels.get(DeploymentLabel.SCALE_UP_COOLDOWN.getLabel()));
			this.scaleDownCooldown = Double.parseDouble(labels.get(DeploymentLabel.SCALE_DOWN_COOLDOWN.getLabel()));

			this.scaleUpLimit = Integer.parseInt(labels.get(DeploymentLabel.SCALE_UP_LIMIT.getLabel()));
			this.scaleDownLimit = Integer.parseInt(labels.get(DeploymentLabel.SCALE_DOWN_LIMIT.getLabel()));
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			ScalingSettings that = (ScalingSettings) o;
			return maxPlayers == that.maxPlayers &&
					minInstances == that.minInstances &&
					maxInstances == that.maxInstances &&
					Double.compare(that.scaleUpThreshold, scaleUpThreshold) == 0 &&
					Double.compare(that.scaleDownThreshold, scaleDownThreshold) == 0 &&
					Double.compare(that.scaleUpCooldown, scaleUpCooldown) == 0 &&
					Double.compare(that.scaleDownCooldown, scaleDownCooldown) == 0 &&
					scaleUpLimit == that.scaleUpLimit &&
					scaleDownLimit == that.scaleDownLimit &&
					strategy == that.strategy;
		}

		@Override
		public int hashCode() {
			return Objects.hash(strategy, maxPlayers, minInstances, maxInstances,
					scaleUpThreshold, scaleDownThreshold,
					scaleUpCooldown, scaleDownCooldown,
					scaleUpLimit, scaleDownLimit);
		}
	}
}
