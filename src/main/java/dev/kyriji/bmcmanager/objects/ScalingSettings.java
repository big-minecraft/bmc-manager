package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;

import java.util.Map;
import java.util.Objects;

public class ScalingSettings {
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