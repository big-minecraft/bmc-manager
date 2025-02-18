package dev.kyriji.bmcmanager.enums;

public enum DeploymentType {
	SCALABLE,
	PERSISTENT,
	PROXY,
	;

	public static DeploymentType getType(String type) {
		for(DeploymentType deploymentType : values()) {
			if(deploymentType.name().equalsIgnoreCase(type)) return deploymentType;
		}
		return null;
	}
}
