package dev.kyriji.bmcmanager.enums;

public enum QueueStrategy {
	SPREAD,
	FILL,
	DYNAMIC_FILL,
	;

	public static QueueStrategy getStrategy(String strategy) {
		for (QueueStrategy queueStrategy : values()) {
			if (queueStrategy.name().equalsIgnoreCase(strategy)) {
				return queueStrategy;
			}
		}
		return null;
	}
}
