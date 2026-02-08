package dev.kyriji.bmcmanager.objects;

/**
 * Response from API to Manager regarding a shutdown proposal
 * Note: This is duplicated from the API library until the API jar is rebuilt
 */
public class ShutdownResponse {
	public enum ResponseType {
		ACCEPT,  // Accept shutdown immediately
		DELAY,   // Request additional time
		VETO     // Request to cancel shutdown
	}

	private final String token;
	private final ResponseType responseType;
	private final Integer requestedSeconds; // Only for DELAY
	private final String reason;            // Optional explanation

	public ShutdownResponse(String token, ResponseType responseType) {
		this(token, responseType, null, null);
	}

	public ShutdownResponse(String token, ResponseType responseType, Integer requestedSeconds, String reason) {
		this.token = token;
		this.responseType = responseType;
		this.requestedSeconds = requestedSeconds;
		this.reason = reason;
	}

	public String getToken() {
		return token;
	}

	public ResponseType getResponseType() {
		return responseType;
	}

	public Integer getRequestedSeconds() {
		return requestedSeconds;
	}

	public String getReason() {
		return reason;
	}

	/**
	 * Parse from Redis message format: "token:responseType[:requestedSeconds[:reason]]"
	 */
	public static ShutdownResponse parse(String message) {
		String[] parts = message.split(":", 4);
		if (parts.length < 2) {
			throw new IllegalArgumentException("Invalid shutdown response format: " + message);
		}

		String token = parts[0];
		ResponseType responseType = ResponseType.valueOf(parts[1]);
		Integer requestedSeconds = parts.length > 2 && !parts[2].isEmpty() ? Integer.parseInt(parts[2]) : null;
		String reason = parts.length > 3 ? parts[3] : null;

		return new ShutdownResponse(token, responseType, requestedSeconds, reason);
	}

	/**
	 * Serialize to Redis message format
	 */
	public String serialize() {
		StringBuilder sb = new StringBuilder();
		sb.append(token).append(":").append(responseType.name());

		if (requestedSeconds != null) {
			sb.append(":").append(requestedSeconds);
			if (reason != null && !reason.isEmpty()) {
				sb.append(":").append(reason);
			}
		} else if (reason != null && !reason.isEmpty()) {
			sb.append("::").append(reason);
		}

		return sb.toString();
	}
}
