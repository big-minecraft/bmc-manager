package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.objects.Game;
import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QueueManager {

	// Track pending reservations: instance UID -> map of (player UUID -> reservation timestamp)
	private static final Map<String, Map<UUID, Long>> pendingReservations = new ConcurrentHashMap<>();

	// Reservation timeout in milliseconds (5 seconds should be plenty for a player to connect)
	private static final long RESERVATION_TIMEOUT_MS = 5_000;

	public static void queuePlayer(UUID player, Game game) {
		// Clean up expired reservations before processing
		cleanupExpiredReservations();

		MinecraftInstance selectedInstance = findInstance(game);

		if (selectedInstance != null) {
			// Add reservation for this player on the selected instance
			addReservation(selectedInstance.getUid(), player);
		}

		sendPlayerToInstance(player, selectedInstance);
	}

	public static MinecraftInstance findInstance(Game game) {
		QueueStrategy strategy = game.getQueueStrategy();

		System.out.println("\n=== QUEUE DEBUG: " + game.getName() + " ===");
		System.out.println("Strategy: " + strategy);
		System.out.println("Max players per instance: " + game.getScalingSettings().maxPlayers);

		MinecraftInstance result = switch(strategy) {
			case SPREAD -> findSpreadInstance(game);
			case FILL -> findFillInstance(game);
		};

		System.out.println("Selected instance: " + (result != null ? result.getName() + " (players: " + result.getPlayers().size() + ", pending: " + getReservationCount(result.getUid()) + ")" : "null"));
		System.out.println("=== END QUEUE DEBUG ===\n");

		return result;
	}

	private static MinecraftInstance findSpreadInstance(Game game) {
		List<MinecraftInstance> instances = game.getInstances();
		MinecraftInstance bestInstance = null;
		int bestEffectiveCount = Integer.MAX_VALUE;

		System.out.println("SPREAD: Evaluating " + instances.size() + " instances (looking for LOWEST player count):");

		for (MinecraftInstance instance : instances) {
			int playerCount = instance.getPlayers().size();
			int pendingCount = getReservationCount(instance.getUid());
			int effectiveCount = playerCount + pendingCount;
			int maxPlayers = game.getScalingSettings().maxPlayers;
			InstanceState state = instance.getState();

			String status;
			if (effectiveCount >= maxPlayers) {
				status = "SKIP (full: " + playerCount + "+" + pendingCount + " pending=" + effectiveCount + "/" + maxPlayers + ")";
			} else if (state != InstanceState.RUNNING) {
				status = "SKIP (state: " + state + ")";
			} else if (effectiveCount < bestEffectiveCount) {
				status = "NEW BEST (effective: " + effectiveCount + " [" + playerCount + "+" + pendingCount + "] < previous best: " + bestEffectiveCount + ")";
				bestInstance = instance;
				bestEffectiveCount = effectiveCount;
			} else {
				status = "NOT BETTER (effective: " + effectiveCount + " >= best: " + bestEffectiveCount + ")";
			}

			System.out.println("  - " + instance.getName() + ": " + status);
		}

		return bestInstance;
	}

	private static MinecraftInstance findFillInstance(Game game) {
		List<MinecraftInstance> instances = game.getInstances();
		MinecraftInstance bestInstance = null;
		int bestEffectiveCount = 0;

		System.out.println("FILL: Evaluating " + instances.size() + " instances (looking for HIGHEST player count with room):");

		for (MinecraftInstance instance : instances) {
			int playerCount = instance.getPlayers().size();
			int pendingCount = getReservationCount(instance.getUid());
			int effectiveCount = playerCount + pendingCount;
			int maxPlayers = game.getScalingSettings().maxPlayers;
			InstanceState state = instance.getState();

			String status;
			if (state != InstanceState.RUNNING) {
				status = "SKIP (state: " + state + ")";
			} else if (effectiveCount >= maxPlayers) {
				status = "SKIP (full: " + playerCount + "+" + pendingCount + " pending=" + effectiveCount + "/" + maxPlayers + ")";
			} else if (effectiveCount >= bestEffectiveCount) {
				status = "NEW BEST (effective: " + effectiveCount + " [" + playerCount + "+" + pendingCount + "] >= previous best: " + bestEffectiveCount + ")";
				bestInstance = instance;
				bestEffectiveCount = effectiveCount;
			} else {
				status = "NOT BETTER (effective: " + effectiveCount + " < best: " + bestEffectiveCount + ")";
			}

			System.out.println("  - " + instance.getName() + ": " + status);
		}

		return bestInstance;
	}

	public static void sendPlayerToInstance(UUID player, MinecraftInstance instance) {
		RedisManager.get().publish(RedisChannel.QUEUE_RESPONSE.getRef(), player.toString() + ":" +
				(instance == null ? "null" : instance.getName()));
	}

	// === Reservation Management ===

	private static void addReservation(String instanceUid, UUID player) {
		pendingReservations
			.computeIfAbsent(instanceUid, k -> new ConcurrentHashMap<>())
			.put(player, System.currentTimeMillis());
		System.out.println("Added reservation for player " + player + " on instance " + instanceUid);
	}

	/**
	 * Release a reservation when a player joins an instance.
	 * Called from PlayerListenerTask when INSTANCE_SWITCH is received.
	 */
	public static void releaseReservation(String instanceUid, UUID player) {
		Map<UUID, Long> instanceReservations = pendingReservations.get(instanceUid);
		if (instanceReservations != null) {
			if (instanceReservations.remove(player) != null) {
				System.out.println("Released reservation for player " + player + " on instance " + instanceUid);
			}
			// Clean up empty maps
			if (instanceReservations.isEmpty()) {
				pendingReservations.remove(instanceUid);
			}
		}
	}

	/**
	 * Release all reservations for a player (e.g., when they disconnect before joining).
	 */
	public static void releaseAllReservations(UUID player) {
		for (Map.Entry<String, Map<UUID, Long>> entry : pendingReservations.entrySet()) {
			if (entry.getValue().remove(player) != null) {
				System.out.println("Released reservation for player " + player + " on instance " + entry.getKey());
			}
			if (entry.getValue().isEmpty()) {
				pendingReservations.remove(entry.getKey());
			}
		}
	}

	private static int getReservationCount(String instanceUid) {
		Map<UUID, Long> instanceReservations = pendingReservations.get(instanceUid);
		return instanceReservations != null ? instanceReservations.size() : 0;
	}

	private static void cleanupExpiredReservations() {
		long now = System.currentTimeMillis();
		int cleaned = 0;

		for (Map.Entry<String, Map<UUID, Long>> entry : pendingReservations.entrySet()) {
			Map<UUID, Long> instanceReservations = entry.getValue();
			Iterator<Map.Entry<UUID, Long>> it = instanceReservations.entrySet().iterator();

			while (it.hasNext()) {
				Map.Entry<UUID, Long> reservation = it.next();
				if (now - reservation.getValue() > RESERVATION_TIMEOUT_MS) {
					it.remove();
					cleaned++;
					System.out.println("Expired reservation for player " + reservation.getKey() + " on instance " + entry.getKey());
				}
			}

			if (instanceReservations.isEmpty()) {
				pendingReservations.remove(entry.getKey());
			}
		}

		if (cleaned > 0) {
			System.out.println("Cleaned up " + cleaned + " expired reservations");
		}
	}
}