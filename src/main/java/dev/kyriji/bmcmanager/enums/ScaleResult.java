package dev.kyriji.bmcmanager.enums;

public enum ScaleResult {

	UP,
	DOWN,
	NO_CHANGE,
	;

	public static ScaleResult getResult(String result) {
		for (ScaleResult scaleResult : values()) {
			if (scaleResult.name().equalsIgnoreCase(result)) {
				return scaleResult;
			}
		}
		return null;
	}
}
