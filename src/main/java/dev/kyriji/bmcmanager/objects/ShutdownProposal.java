package dev.kyriji.bmcmanager.objects;

/**
 * Message sent from Manager to API to propose a graceful shutdown
 * Note: This is duplicated from the API library until the API jar is rebuilt
 */
public class ShutdownProposal {
	private final String targetInstanceIp;
	private final String token;
	private final String reason;
	private final int maxDelaySeconds;

	public ShutdownProposal(String targetInstanceIp, String token, String reason, int maxDelaySeconds) {
		this.targetInstanceIp = targetInstanceIp;
		this.token = token;
		this.reason = reason;
		this.maxDelaySeconds = maxDelaySeconds;
	}

	public String getTargetInstanceIp() {
		return targetInstanceIp;
	}

	public String getToken() {
		return token;
	}

	public String getReason() {
		return reason;
	}

	public int getMaxDelaySeconds() {
		return maxDelaySeconds;
	}

	/**
	 * Parse from Redis message format: "targetInstanceIp:token:reason:maxDelaySeconds"
	 */
	public static ShutdownProposal parse(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length != 4) {
			throw new IllegalArgumentException("Invalid shutdown proposal format: " + message);
		}
		return new ShutdownProposal(parts[0], parts[1], parts[2], Integer.parseInt(parts[3]));
	}

	/**
	 * Serialize to Redis message format
	 */
	public String serialize() {
		return targetInstanceIp + ":" + token + ":" + reason + ":" + maxDelaySeconds;
	}
}
