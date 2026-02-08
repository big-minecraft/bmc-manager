package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.controllers.ShutdownNegotiationManager;

import java.util.List;

/**
 * Background task that periodically checks shutdown timeouts and issues final shutdown commands.
 *
 * This task runs every 5 seconds and:
 * - Checks if any pending shutdowns have reached their block_until deadline
 * - Checks if any Minecraft instances have 0 players and can be shutdown early
 * - Issues final shutdown commands when conditions are met
 * - Cleans up completed shutdowns
 */
public class ShutdownTimeoutCheckerTask {
	private static final int CHECK_INTERVAL_MS = 5000; // 5 seconds

	public ShutdownTimeoutCheckerTask() {
		new Thread(() -> {
			System.out.println("ShutdownTimeoutCheckerTask started - checking every " + CHECK_INTERVAL_MS + "ms");

			while (true) {
				try {
					// Check timeouts and issue final shutdowns
					List<String> finalizedShutdowns = ShutdownNegotiationManager.get().checkTimeoutsAndIssueFinalShutdowns();

					if (!finalizedShutdowns.isEmpty()) {
						System.out.println("Issued final shutdown commands to " + finalizedShutdowns.size() + " instance(s)");
					}

					// Clean up completed shutdowns
					ShutdownNegotiationManager.get().cleanupCompletedShutdowns();

					Thread.sleep(CHECK_INTERVAL_MS);
				} catch (InterruptedException e) {
					System.err.println("ShutdownTimeoutCheckerTask interrupted");
					break;
				} catch (Exception e) {
					System.err.println("Error in ShutdownTimeoutCheckerTask: " + e.getMessage());
					e.printStackTrace();

					// Continue running even if there's an error
					try {
						Thread.sleep(CHECK_INTERVAL_MS);
					} catch (InterruptedException ie) {
						break;
					}
				}
			}
		}).start();
	}
}
