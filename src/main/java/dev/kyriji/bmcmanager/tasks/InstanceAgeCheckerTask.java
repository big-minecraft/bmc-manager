package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.ShutdownNegotiationManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import dev.kyriji.bmcmanager.utils.DurationParser;

/**
 * Background task that checks whether any running instance has exceeded the
 * maxInstanceAge defined on its GameServer CRD, and initiates a graceful
 * shutdown via ShutdownNegotiationManager when it has.
 *
 * Runs every CHECK_INTERVAL_MS milliseconds.
 */
public class InstanceAgeCheckerTask {
	private static final int CHECK_INTERVAL_MS = 30_000; // 30 seconds

	public InstanceAgeCheckerTask() {
		new Thread(() -> {
			System.out.println("InstanceAgeCheckerTask started - checking every " + CHECK_INTERVAL_MS + "ms");

			while (true) {
				try {
					checkInstanceAges();
					Thread.sleep(CHECK_INTERVAL_MS);
				} catch (InterruptedException e) {
					System.err.println("InstanceAgeCheckerTask interrupted");
					break;
				} catch (Exception e) {
					System.err.println("Error in InstanceAgeCheckerTask: " + e.getMessage());
					e.printStackTrace();

					try {
						Thread.sleep(CHECK_INTERVAL_MS);
					} catch (InterruptedException ie) {
						break;
					}
				}
			}
		}).start();
	}

	private void checkInstanceAges() {
		long now = System.currentTimeMillis();

		for (GameServerWrapper<?> wrapper : BMCManager.gameServerManager.getGameServers()) {
			String maxInstanceAge = wrapper.getGameServer().getSpec().getMaxInstanceAge();
			if (maxInstanceAge == null || maxInstanceAge.isBlank()) continue;

			long maxAgeMillis;
			try {
				maxAgeMillis = DurationParser.parseToMillis(maxInstanceAge);
			} catch (IllegalArgumentException e) {
				System.err.println("Invalid maxInstanceAge \"" + maxInstanceAge + "\" on GameServer " +
						wrapper.getName() + ": " + e.getMessage());
				continue;
			}

			for (Instance instance : wrapper.getInstances()) {
				InstanceState state = instance.getState();
				if (state == InstanceState.DRAINING ||
				    state == InstanceState.STOPPING ||
				    state == InstanceState.STOPPED) {
					continue;
				}

				if (ShutdownNegotiationManager.get().isPendingShutdown(instance.getUid())) {
					continue;
				}

				Long creationTime = BMCManager.serverDiscovery.getPodCreationTime(instance.getUid());
				if (creationTime == null) continue;

				long age = now - creationTime;
				if (age >= maxAgeMillis) {
					System.out.println("Instance " + instance.getName() + " has exceeded maxInstanceAge of \"" +
							maxInstanceAge + "\" (age=" + formatDuration(age) + ") - proposing shutdown");
					ShutdownNegotiationManager.get().proposeShutdown(instance, "max_instance_age");
				}
			}
		}
	}

	private static String formatDuration(long millis) {
		long seconds = millis / 1000;
		long days = seconds / 86_400;
		seconds %= 86_400;
		long hours = seconds / 3_600;
		seconds %= 3_600;
		long minutes = seconds / 60;
		seconds %= 60;

		StringBuilder sb = new StringBuilder();
		if (days > 0) sb.append(days).append("d ");
		if (hours > 0) sb.append(hours).append("h ");
		if (minutes > 0) sb.append(minutes).append("m ");
		sb.append(seconds).append("s");
		return sb.toString().trim();
	}
}
