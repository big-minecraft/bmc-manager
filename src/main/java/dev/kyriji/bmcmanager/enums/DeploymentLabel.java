package dev.kyriji.bmcmanager.enums;

public enum DeploymentLabel {

	SERVER_DISCOVERY(getPrefix() + "enable-server-discovery"),
	PANEL_DISCOVERY(getPrefix() + "enable-panel-discovery"),
	INITIAL_SERVER(getPrefix() + "initial-server"),
	QUEUE_STRATEGY(getPrefix() + "queue-strategy"),
	MAX_PLAYERS(getPrefix() + "max-players")
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
