package dev.kyriji.bmcmanager.enums;

public enum DeploymentType {
	PROXY,
	SCALABLE,
	PERSISTENT,
	PROCESS
	;

	public static DeploymentType getType(String type) {
		for(DeploymentType deploymentType : values()) {
			if(deploymentType.name().equalsIgnoreCase(type)) return deploymentType;
		}
		return null;
	}
}
