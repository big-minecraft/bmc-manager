package dev.kyriji.bmcmanager.objects;

/**
 * Message sent from Manager to API to propose a graceful shutdown
 * Note: This is duplicated from the API library until the API jar is rebuilt
 */
public class ShutdownProposal {
	private final String token;
	private final String reason;
	private final int maxDelaySeconds;

	public ShutdownProposal(String token, String reason, int maxDelaySeconds) {
		this.token = token;
		this.reason = reason;
		this.maxDelaySeconds = maxDelaySeconds;
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
	 * Parse from Redis message format: "token:reason:maxDelaySeconds"
	 */
	public static ShutdownProposal parse(String message) {
		String[] parts = message.split(":", 3);
		if (parts.length != 3) {
			throw new IllegalArgumentException("Invalid shutdown proposal format: " + message);
		}
		return new ShutdownProposal(parts[0], parts[1], Integer.parseInt(parts[2]));
	}

	/**
	 * Serialize to Redis message format
	 */
	public String serialize() {
		return token + ":" + reason + ":" + maxDelaySeconds;
	}
}
