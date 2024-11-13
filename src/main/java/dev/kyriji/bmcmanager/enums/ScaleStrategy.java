package dev.kyriji.bmcmanager.enums;

public enum ScaleStrategy {
	THRESHOLD,
	TREND,
	;

	public static ScaleStrategy getStrategy(String strategy) {
		for (ScaleStrategy queueStrategy : values()) {
			if (queueStrategy.name().equalsIgnoreCase(strategy)) {
				return queueStrategy;
			}
		}
		return null;
	}
}
