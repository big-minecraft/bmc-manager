package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.objects.Deployment;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.List;
import java.util.UUID;

public class QueueManager {

	public static void queuePlayer(UUID player, Deployment deployment) {
		MinecraftInstance selectedInstance = findInstance(deployment);

		sendPlayerToInstance(player, selectedInstance);
	}

	public static MinecraftInstance findInstance(Deployment deployment) {
		QueueStrategy strategy = deployment.getQueueStrategy();

		return switch(strategy) {
			case SPREAD -> findSpreadInstance(deployment);
			case FILL -> findFillInstance(deployment);
		};
	}

	private static MinecraftInstance findSpreadInstance(Deployment deployment) {
		List<MinecraftInstance> instances = deployment.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = Integer.MAX_VALUE;

		for (MinecraftInstance instance : instances) {
			if (instance.getPlayers().size() >= deployment.getMaxPlayers()) continue;
			if (instance.getState() != InstanceState.RUNNING) continue;

			if (instance.getPlayers().size() < bestPlayerCount) {
				bestInstance = instance;
				bestPlayerCount = instance.getPlayers().size();
			}
		}

		return bestInstance;
	}

	private static MinecraftInstance findFillInstance(Deployment deployment) {
		List<MinecraftInstance> instances = deployment.getInstances();
		MinecraftInstance bestInstance = null;
		int bestPlayerCount = 0;

		for (MinecraftInstance instance : instances) {
			if (instance.getState() != InstanceState.RUNNING) continue;

			if (instance.getPlayers().size() < deployment.getMaxPlayers() &&
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