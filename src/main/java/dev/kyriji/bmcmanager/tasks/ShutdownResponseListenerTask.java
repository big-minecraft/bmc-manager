package dev.kyriji.bmcmanager.tasks;

import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.controllers.ShutdownNegotiationManager;
import dev.kyriji.bmcmanager.objects.ShutdownResponse;
import redis.clients.jedis.JedisPubSub;

/**
 * Listens for shutdown responses from instances and forwards them to ShutdownNegotiationManager.
 */
public class ShutdownResponseListenerTask {
	private static final String SHUTDOWN_RESPONSE_CHANNEL = "shutdown:response";

	public ShutdownResponseListenerTask() {
		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				try {
					ShutdownResponse response = ShutdownResponse.parse(message);
					ShutdownNegotiationManager.get().handleResponse(response);
				} catch (Exception e) {
					System.err.println("Error processing shutdown response: " + e.getMessage());
					e.printStackTrace();
				}
			}
		}, SHUTDOWN_RESPONSE_CHANNEL)).start();

		System.out.println("ShutdownResponseListenerTask started - listening on channel: " + SHUTDOWN_RESPONSE_CHANNEL);
	}
}
