package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.objects.Game;
import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;

import java.util.List;
import java.util.UUID;

public class QueueManager {

	public static void queuePlayer(UUID player, Game game) {
		MinecraftInstance selectedInstance = findInstance(game);

		sendPlayerToInstance(player, selectedInstance);
	}

	public static MinecraftInstance findInstance(Game game) {
		QueueStrategy strategy = game.getQueueStrategy();

		return switch(strategy) {
			case SPREAD -> findSpreadInstance(game);
			case FILL -> findFillInstance(game);
		};
	}

	private static MinecraftInstance findSpreadInstance(Game game) {
		List<MinecraftInstance> instances = game.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = Integer.MAX_VALUE;

		System.out.println("SPREAD: Evaluating " + instances.size() + " instances (looking for LOWEST player count):");

		for (MinecraftInstance instance : instances) {
			int playerCount = instance.getPlayers().size();
			int maxPlayers = game.getScalingSettings().maxPlayers;
			InstanceState state = instance.getState();

			String status;
			if (playerCount >= maxPlayers) {
				status = "SKIP (full: " + playerCount + "/" + maxPlayers + ")";
			} else if (state != InstanceState.RUNNING) {
				status = "SKIP (state: " + state + ")";
			} else if (playerCount < bestPlayerCount) {
				status = "NEW BEST (players: " + playerCount + " < previous best: " + bestPlayerCount + ")";
				bestInstance = instance;
				bestPlayerCount = playerCount;
			} else {
				status = "NOT BETTER (players: " + playerCount + " >= best: " + bestPlayerCount + ")";
			}

			System.out.println("  - " + instance.getName() + ": " + status);
		}

		return bestInstance;
	}

	private static MinecraftInstance findFillInstance(Game game) {
		List<MinecraftInstance> instances = game.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = 0;

		for (MinecraftInstance instance : instances) {
			int playerCount = instance.getPlayers().size();
			int maxPlayers = game.getScalingSettings().maxPlayers;
			InstanceState state = instance.getState();

			String status;
			if (state != InstanceState.RUNNING) {
				status = "SKIP (state: " + state + ")";
			} else if (playerCount >= maxPlayers) {
				status = "SKIP (full: " + playerCount + "/" + maxPlayers + ")";
			} else if (playerCount >= bestPlayerCount) {
				status = "NEW BEST (players: " + playerCount + " >= previous best: " + bestPlayerCount + ")";
				bestInstance = instance;
				bestPlayerCount = playerCount;
			} else {
				status = "NOT BETTER (players: " + playerCount + " < best: " + bestPlayerCount + ")";
			}

			System.out.println("  - " + instance.getName() + ": " + status);
		}

		return bestInstance;
	}

	public static void sendPlayerToInstance(UUID player, MinecraftInstance instance) {
		RedisManager.get().publish(RedisChannel.QUEUE_RESPONSE.getRef(), player.toString() + ":" +
				(instance == null ? "null" : instance.getName()));
	}
}