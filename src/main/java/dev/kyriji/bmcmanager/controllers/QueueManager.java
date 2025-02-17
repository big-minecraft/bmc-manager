package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.objects.Game;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

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

		for (MinecraftInstance instance : instances) {
			if (instance.getPlayers().size() >= game.getScalingSettings().maxPlayers) continue;
			if (instance.getState() != InstanceState.RUNNING) continue;

			if (instance.getPlayers().size() < bestPlayerCount) {
				bestInstance = instance;
				bestPlayerCount = instance.getPlayers().size();
			}
		}

		return bestInstance;
	}

	private static MinecraftInstance findFillInstance(Game game) {
		List<MinecraftInstance> instances = game.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = 0;

		for (MinecraftInstance instance : instances) {
			if (instance.getState() != InstanceState.RUNNING) continue;

			if (instance.getPlayers().size() < game.getScalingSettings().maxPlayers &&
					instance.getPlayers().size() >= bestPlayerCount) {
				bestInstance = instance;
				bestPlayerCount = instance.getPlayers().size();
			}
		}

		return bestInstance;
	}

	public static void sendPlayerToInstance(UUID player, MinecraftInstance instance) {
		RedisManager.get().publish(RedisChannel.QUEUE_RESPONSE.getRef(), player.toString() + ":" +
				(instance == null ? "null" : instance.getName()));
	}
}