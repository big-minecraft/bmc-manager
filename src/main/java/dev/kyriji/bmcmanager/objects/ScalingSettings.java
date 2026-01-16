package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.crd.GameServerSpec;
import dev.kyriji.bmcmanager.enums.ScaleStrategy;

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

	public ScalingSettings(GameServerSpec.ScalingSpec scalingSpec) {
		if (scalingSpec == null) {
			// Default values for non-scaling deployments
			this.strategy = ScaleStrategy.THRESHOLD;
			this.maxPlayers = 100;
			this.minInstances = 1;
			this.maxInstances = 1;
			this.scaleUpThreshold = 80;
			this.scaleDownThreshold = 20;
			this.scaleUpCooldown = 60;
			this.scaleDownCooldown = 60;
			this.scaleUpLimit = 1;
			this.scaleDownLimit = 1;
			return;
		}

		this.strategy = ScaleStrategy.getStrategy(scalingSpec.getStrategy());

		this.maxPlayers = scalingSpec.getMaxPlayers() != null ? scalingSpec.getMaxPlayers() : 100;
		this.minInstances = scalingSpec.getMinInstances() != null ? scalingSpec.getMinInstances() : 1;
		this.maxInstances = scalingSpec.getMaxInstances() != null ? scalingSpec.getMaxInstances() : Integer.MAX_VALUE;

		this.scaleUpThreshold = scalingSpec.getScaleUpThreshold() != null ? scalingSpec.getScaleUpThreshold() : 80;
		this.scaleDownThreshold = scalingSpec.getScaleDownThreshold() != null ? scalingSpec.getScaleDownThreshold() : 20;

		this.scaleUpCooldown = scalingSpec.getScaleUpCooldown() != null ? scalingSpec.getScaleUpCooldown() : 60;
		this.scaleDownCooldown = scalingSpec.getScaleDownCooldown() != null ? scalingSpec.getScaleDownCooldown() : 60;

		this.scaleUpLimit = scalingSpec.getScaleUpLimit() != null ? scalingSpec.getScaleUpLimit() : 1;
		this.scaleDownLimit = scalingSpec.getScaleDownLimit() != null ? scalingSpec.getScaleDownLimit() : 1;
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