package dev.kyriji.bmcmanager.enums;

public enum DeploymentLabel {

	SERVER_DISCOVERY(getPrefix() + "enable-server-discovery"),
	PANEL_DISCOVERY(getPrefix() + "enable-panel-discovery"),
	INITIAL_SERVER(getPrefix() + "initial-server"),
	QUEUE_STRATEGY(getPrefix() + "queue-strategy"),
	SCALE_STRATEGY(getPrefix() + "scale-strategy"),
	MAX_PLAYERS(getPrefix() + "max-players"),
	MIN_INSTANCES(getPrefix() + "min-instances"),
	MAX_INSTANCES(getPrefix() + "max-instances"),
	SCALE_UP_THRESHOLD(getPrefix() + "scale-up-threshold"),
	SCALE_DOWN_THRESHOLD(getPrefix() + "scale-down-threshold"),
	SCALE_UP_COOLDOWN(getPrefix() + "scale-up-cooldown"),
	SCALE_DOWN_COOLDOWN(getPrefix() + "scale-down-cooldown"),
	SCALE_UP_LIMIT(getPrefix() + "scale-up-limit"),
	SCALE_DOWN_LIMIT(getPrefix() + "scale-down-limit"),
	;

	private final String label;

	DeploymentLabel(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static String getPrefix() {
		return "kyriji.dev/";
	}
}
