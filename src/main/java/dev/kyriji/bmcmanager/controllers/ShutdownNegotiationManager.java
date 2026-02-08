package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.objects.ShutdownProposal;
import dev.kyriji.bmcmanager.objects.ShutdownResponse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages graceful shutdown negotiation with game server instances.
 *
 * This manager:
 * - Proposes shutdowns to instances
 * - Tracks shutdown state and deadlines
 * - Processes responses (ACCEPT/DELAY/VETO)
 * - Enforces timeout deadlines
 * - Issues final shutdown commands
 */
public class ShutdownNegotiationManager {
	private static ShutdownNegotiationManager instance;

	// TODO: Future enhancement - implement heartbeat monitoring
	// Track last_heartbeat timestamp in Redis for each instance
	// If heartbeat lost (e.g., > 30s old), force shutdown
	// This prevents stuck instances from blocking scale-down indefinitely

	private static final int DEFAULT_MAX_DELAY_SECONDS = 600; // 10 minutes
	private static final int VETO_MINIMAL_DELAY_SECONDS = 60; // 1 minute if veto downgraded
	private static final int RESPONSE_TIMEOUT_SECONDS = 10; // If no response within 10s, shutdown immediately
	private static final String SHUTDOWN_PROPOSE_CHANNEL = "shutdown:propose";
	private static final String SHUTDOWN_FINAL_CHANNEL = "shutdown:final";

	/**
	 * Tracks active shutdowns: token -> shutdown state
	 */
	private final Map<String, PendingShutdown> pendingShutdowns = new ConcurrentHashMap<>();

	/**
	 * Tracks which instance each shutdown token belongs to: token -> instance UID
	 */
	private final Map<String, String> tokenToInstanceUid = new ConcurrentHashMap<>();

	private ShutdownNegotiationManager() {
	}

	public static synchronized ShutdownNegotiationManager get() {
		if (instance == null) {
			instance = new ShutdownNegotiationManager();
		}
		return instance;
	}

	/**
	 * Propose a graceful shutdown to an instance.
	 *
	 * @param instance The instance to shut down
	 * @param reason Reason for shutdown (e.g., "scale_down")
	 * @return The shutdown token for tracking
	 */
	public String proposeShutdown(Instance instance, String reason) {
		return proposeShutdown(instance, reason, DEFAULT_MAX_DELAY_SECONDS);
	}

	/**
	 * Propose a graceful shutdown to an instance with a custom max delay.
	 *
	 * @param instance The instance to shut down
	 * @param reason Reason for shutdown
	 * @param maxDelaySeconds Maximum delay allowed
	 * @return The shutdown token for tracking
	 */
	public String proposeShutdown(Instance instance, String reason, int maxDelaySeconds) {
		String token = UUID.randomUUID().toString();
		long blockUntil = System.currentTimeMillis() + (maxDelaySeconds * 1000L);

		// Create pending shutdown state
		PendingShutdown pendingShutdown = new PendingShutdown(token, instance.getUid(), reason, blockUntil);
		pendingShutdowns.put(token, pendingShutdown);
		tokenToInstanceUid.put(token, instance.getUid());

		// Update instance state to BLOCKED in Redis
		instance.setState(InstanceState.BLOCKED);
		RedisManager.get().updateInstance(instance);

		// Store shutdown metadata in Redis
		String key = "instance:" + instance.getUid() + ":" + instance.getDeployment();
		RedisManager.get().hset(key, "block_until", String.valueOf(blockUntil));
		RedisManager.get().hset(key, "shutdown_reason", reason);
		RedisManager.get().hset(key, "shutdown_token", token);
		// TODO: Store last_heartbeat timestamp here for future heartbeat monitoring

		// Send shutdown proposal to instance
		ShutdownProposal proposal = new ShutdownProposal(token, reason, maxDelaySeconds);
		RedisManager.get().publish(SHUTDOWN_PROPOSE_CHANNEL, proposal.serialize());

		System.out.println("Proposed shutdown for instance " + instance.getName() +
		                   " (UID: " + instance.getUid() + ") - Token: " + token +
		                   ", Reason: " + reason + ", Max delay: " + maxDelaySeconds + "s");

		return token;
	}

	/**
	 * Handle a shutdown response from an instance.
	 * Called by the shutdown response listener.
	 *
	 * @param response The response from the instance
	 */
	public void handleResponse(ShutdownResponse response) {
		String token = response.getToken();
		PendingShutdown pendingShutdown = pendingShutdowns.get(token);

		if (pendingShutdown == null) {
			System.err.println("Received shutdown response for unknown token: " + token);
			return;
		}

		if (pendingShutdown.hasResponded) {
			System.out.println("Ignoring duplicate response for token: " + token);
			return;
		}

		pendingShutdown.hasResponded = true;
		String instanceUid = pendingShutdown.instanceUid;

		switch (response.getResponseType()) {
			case ACCEPT:
				System.out.println("Instance " + instanceUid + " accepted shutdown (Token: " + token + ")");
				// No delay needed, can proceed to final shutdown when ready
				break;

			case DELAY:
				int requestedSeconds = response.getRequestedSeconds() != null ? response.getRequestedSeconds() : 0;
				int originalMaxDelay = (int) ((pendingShutdown.blockUntil - pendingShutdown.proposalTime) / 1000);
				int grantedSeconds = Math.min(requestedSeconds, originalMaxDelay);

				// Update block_until
				long newBlockUntil = pendingShutdown.proposalTime + (grantedSeconds * 1000L);
				pendingShutdown.blockUntil = newBlockUntil;

				System.out.println("Instance " + instanceUid + " requested shutdown delay of " + requestedSeconds + "s" +
				                   " (granted: " + grantedSeconds + "s) - Reason: " + response.getReason() +
				                   " (Token: " + token + ")");

				// Update Redis block_until
				Instance instance = getInstanceByUid(instanceUid);
				if (instance != null) {
					String key = "instance:" + instance.getUid() + ":" + instance.getDeployment();
					RedisManager.get().hset(key, "block_until", String.valueOf(newBlockUntil));
				}
				break;

			case VETO:
				System.out.println("Instance " + instanceUid + " vetoed shutdown - Reason: " + response.getReason() +
				                   " (Token: " + token + ")");
				System.out.println("Manager retains final authority - downgrading to minimal delay of " +
				                   VETO_MINIMAL_DELAY_SECONDS + "s");

				// Downgrade veto to minimal delay - manager retains authority
				long minimalBlockUntil = System.currentTimeMillis() + (VETO_MINIMAL_DELAY_SECONDS * 1000L);
				pendingShutdown.blockUntil = minimalBlockUntil;

				// Update Redis
				Instance vetoInstance = getInstanceByUid(instanceUid);
				if (vetoInstance != null) {
					String key = "instance:" + vetoInstance.getUid() + ":" + vetoInstance.getDeployment();
					RedisManager.get().hset(key, "block_until", String.valueOf(minimalBlockUntil));
				}
				break;
		}
	}

	/**
	 * Check all pending shutdowns and issue final shutdown commands for instances that:
	 * - Haven't responded within RESPONSE_TIMEOUT_SECONDS (server not running API or unresponsive), OR
	 * - Have reached their block_until deadline, OR
	 * - Have 0 players (for MinecraftInstance)
	 *
	 * Should be called periodically (e.g., every 5 seconds) by a background task.
	 *
	 * @return List of instance UIDs that were issued final shutdown commands
	 */
	public java.util.List<String> checkTimeoutsAndIssueFinalShutdowns() {
		java.util.List<String> finalizedShutdowns = new java.util.ArrayList<>();
		long now = System.currentTimeMillis();

		for (PendingShutdown pendingShutdown : pendingShutdowns.values()) {
			if (pendingShutdown.finalShutdownSent) {
				continue; // Already sent final shutdown
			}

			Instance instance = getInstanceByUid(pendingShutdown.instanceUid);
			if (instance == null) {
				// Instance no longer exists, clean up
				pendingShutdowns.remove(pendingShutdown.token);
				tokenToInstanceUid.remove(pendingShutdown.token);
				continue;
			}

			boolean shouldShutdown = false;
			String shutdownReason = null;

			// Check #1: Response timeout - server not running API or unresponsive
			// If no response received within RESPONSE_TIMEOUT_SECONDS, shutdown immediately
			if (!pendingShutdown.hasResponded) {
				long timeSinceProposal = now - pendingShutdown.proposalTime;
				if (timeSinceProposal >= (RESPONSE_TIMEOUT_SECONDS * 1000L)) {
					System.out.println("No response received from instance " + instance.getName() +
					                   " within " + RESPONSE_TIMEOUT_SECONDS + "s - server may not be running shutdown negotiation API");
					shouldShutdown = true;
					shutdownReason = "no_response_timeout";
				}
			}

			// Check #2: Hard deadline reached
			if (now >= pendingShutdown.blockUntil) {
				System.out.println("Shutdown deadline reached for instance " + instance.getName() +
				                   " (UID: " + pendingShutdown.instanceUid + ")");
				shouldShutdown = true;
				shutdownReason = "deadline_reached";
			}

			// Check #3: Players == 0 (for Minecraft instances)
			if (instance instanceof MinecraftInstance minecraftInstance) {
				if (minecraftInstance.getPlayers().isEmpty()) {
					System.out.println("Instance " + instance.getName() + " has 0 players, proceeding with shutdown");
					shouldShutdown = true;
					shutdownReason = "zero_players";
				}
			}

			if (shouldShutdown) {
				issueFinalShutdown(instance, pendingShutdown.token);
				pendingShutdown.finalShutdownSent = true;
				finalizedShutdowns.add(pendingShutdown.instanceUid);

				// Log the reason for shutdown
				if (shutdownReason != null) {
					System.out.println("Final shutdown issued for " + instance.getName() + " - Reason: " + shutdownReason);
				}
			}
		}

		return finalizedShutdowns;
	}

	/**
	 * Issue a final shutdown command to an instance.
	 *
	 * @param instance The instance to shut down
	 * @param token The shutdown token
	 */
	private void issueFinalShutdown(Instance instance, String token) {
		System.out.println("Issuing final shutdown to instance " + instance.getName() +
		                   " (UID: " + instance.getUid() + ") - Token: " + token);

		// Update instance state to STOPPING
		instance.setState(InstanceState.STOPPING);
		RedisManager.get().updateInstance(instance);

		// Send final shutdown command (message is just the token)
		RedisManager.get().publish(SHUTDOWN_FINAL_CHANNEL, token);
	}

	/**
	 * Check if an instance has a pending shutdown.
	 *
	 * @param instanceUid The instance UID
	 * @return True if shutdown is pending
	 */
	public boolean isPendingShutdown(String instanceUid) {
		return pendingShutdowns.values().stream()
				.anyMatch(ps -> ps.instanceUid.equals(instanceUid));
	}

	/**
	 * Get the shutdown token for an instance if it has a pending shutdown.
	 *
	 * @param instanceUid The instance UID
	 * @return The shutdown token, or null if no pending shutdown
	 */
	public String getShutdownToken(String instanceUid) {
		return pendingShutdowns.values().stream()
				.filter(ps -> ps.instanceUid.equals(instanceUid))
				.map(ps -> ps.token)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Cancel a pending shutdown (e.g., if scaling decision changes).
	 *
	 * @param token The shutdown token to cancel
	 */
	public void cancelShutdown(String token) {
		PendingShutdown pendingShutdown = pendingShutdowns.remove(token);
		if (pendingShutdown != null) {
			tokenToInstanceUid.remove(token);

			Instance instance = getInstanceByUid(pendingShutdown.instanceUid);
			if (instance != null) {
				// Revert to RUNNING state
				instance.setState(InstanceState.RUNNING);
				RedisManager.get().updateInstance(instance);

				// Clear shutdown metadata from Redis
				String key = "instance:" + instance.getUid() + ":" + instance.getDeployment();
				RedisManager.get().withRedis(jedis -> {
					jedis.hdel(key, "block_until", "shutdown_reason", "shutdown_token");
				});

				System.out.println("Cancelled shutdown for instance " + instance.getName() +
				                   " (Token: " + token + ")");
			}
		}
	}

	/**
	 * Clean up completed shutdowns (instances that have been deleted).
	 * Should be called periodically.
	 */
	public void cleanupCompletedShutdowns() {
		java.util.List<String> tokensToRemove = new java.util.ArrayList<>();

		for (Map.Entry<String, PendingShutdown> entry : pendingShutdowns.entrySet()) {
			String token = entry.getKey();
			PendingShutdown pendingShutdown = entry.getValue();

			if (pendingShutdown.finalShutdownSent) {
				// Check if instance still exists
				Instance instance = getInstanceByUid(pendingShutdown.instanceUid);
				if (instance == null || instance.getState() == InstanceState.STOPPED) {
					tokensToRemove.add(token);
				}
			}
		}

		for (String token : tokensToRemove) {
			pendingShutdowns.remove(token);
			tokenToInstanceUid.remove(token);
		}
	}

	/**
	 * Helper to get an instance by UID from InstanceManager.
	 */
	private Instance getInstanceByUid(String uid) {
		return BMCManager.instanceManager.getInstances().stream()
				.filter(i -> i.getUid().equals(uid))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Internal class to track pending shutdown state.
	 */
	private static class PendingShutdown {
		final String token;
		final String instanceUid;
		final String reason;
		final long proposalTime;
		long blockUntil;
		boolean hasResponded = false;
		boolean finalShutdownSent = false;

		PendingShutdown(String token, String instanceUid, String reason, long blockUntil) {
			this.token = token;
			this.instanceUid = instanceUid;
			this.reason = reason;
			this.proposalTime = System.currentTimeMillis();
			this.blockUntil = blockUntil;
		}
	}
}
