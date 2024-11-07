package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.objects.Gamemode;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.List;
import java.util.UUID;

public class QueueManager {

	public static void queuePlayer(UUID player, Gamemode gamemode) {
		QueueStrategy strategy = gamemode.getQueueStrategy();
		MinecraftInstance selectedInstance = findInstance(gamemode);

		sendPlayerToInstance(player, selectedInstance);
	}

	public static MinecraftInstance findInstance(Gamemode gamemode) {
		QueueStrategy strategy = gamemode.getQueueStrategy();

		return switch(strategy) {
			case SPREAD -> findSpreadInstance(gamemode);
			case FILL -> findFillInstance(gamemode);
			case DYNAMIC_FILL -> findDynamicFillInstance(gamemode);
		};
	}

	private static MinecraftInstance findSpreadInstance(Gamemode gamemode) {
		List<MinecraftInstance> instances = gamemode.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = Integer.MAX_VALUE;

		for (MinecraftInstance instance : instances) {
			if (instance.getPlayers().size() < bestPlayerCount) {
				bestInstance = instance;
				bestPlayerCount = instance.getPlayers().size();
			}
		}

		return bestInstance;
	}

	private static MinecraftInstance findFillInstance(Gamemode gamemode) {
		List<MinecraftInstance> instances = gamemode.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = 0;

		for (MinecraftInstance instance : instances) {
			if (instance.getPlayers().size() < gamemode.getMaxPlayers() &&
					instance.getPlayers().size() >= bestPlayerCount) {
				bestInstance = instance;
				bestPlayerCount = instance.getPlayers().size();
			}
		}

		return bestInstance;
	}

	private static MinecraftInstance findDynamicFillInstance(Gamemode gamemode) {
		//TODO: Deal with blocked instances
		return null;
	}

	public static void sendPlayerToInstance(UUID player, MinecraftInstance instance) {
		RedisManager.get().publish("queue-response", player.toString() + ":" +
				(instance == null ? "null" : instance.getName()));
	}
}