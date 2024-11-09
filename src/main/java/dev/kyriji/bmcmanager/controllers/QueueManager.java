package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.objects.Gamemode;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.List;
import java.util.UUID;

public class QueueManager {

	public static void queuePlayer(UUID player, Gamemode gamemode) {
		MinecraftInstance selectedInstance = findInstance(gamemode);

		sendPlayerToInstance(player, selectedInstance);
	}

	public static MinecraftInstance findInstance(Gamemode gamemode) {
		QueueStrategy strategy = gamemode.getQueueStrategy();

		return switch(strategy) {
			case SPREAD -> findSpreadInstance(gamemode);
			case FILL -> findFillInstance(gamemode);
		};
	}

	private static MinecraftInstance findSpreadInstance(Gamemode gamemode) {
		List<MinecraftInstance> instances = gamemode.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = Integer.MAX_VALUE;

		for (MinecraftInstance instance : instances) {
			if (instance.getPlayers().size() >= gamemode.getMaxPlayers()) continue;
			if (instance.getState() != InstanceState.RUNNING) continue;

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
			if (instance.getState() != InstanceState.RUNNING) continue;

			if (instance.getPlayers().size() < gamemode.getMaxPlayers() &&
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