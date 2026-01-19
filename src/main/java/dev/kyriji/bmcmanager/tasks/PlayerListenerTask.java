package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.GameServerManager;
import dev.kyriji.bmcmanager.controllers.QueueManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerListenerTask {
	Gson gson = new Gson();

	public PlayerListenerTask() {
		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");

				UUID playerId = UUID.fromString(parts[0]);
				String username = parts[1];
				String proxyIP = parts[2];

				Instance proxyInstance = BMCManager.instanceManager.getFromIP(proxyIP);
				if(proxyInstance == null) return;

				if(!(proxyInstance instanceof MinecraftInstance minecraftInstance)) return;

				minecraftInstance.addPlayer(playerId, username);
				RedisManager.get().updateInstance(minecraftInstance);

				updateGameServer(minecraftInstance);
			}
		}, RedisChannel.PROXY_CONNECT.getRef())).start();

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");

				UUID playerId = UUID.fromString(parts[0]);
				String username = parts[1];
				String proxyIP = parts[2];

				Instance proxyInstance = BMCManager.instanceManager.getFromIP(proxyIP);
				if(proxyInstance == null) return;

				if(!(proxyInstance instanceof MinecraftInstance minecraftInstance)) return;

				minecraftInstance.removePlayer(playerId);
				RedisManager.get().updateInstance(minecraftInstance);

				removePlayerFromInstance(playerId, true);
				updateGameServer(minecraftInstance);

				// Release any pending queue reservations for this player
				QueueManager.releaseAllReservations(playerId);
			}
		}, RedisChannel.PROXY_DISCONNECT.getRef())).start();

		new Thread(() -> RedisManager.get().subscribe(new JedisPubSub() {
			@Override
			public void onMessage(String channel, String message) {
				String[] parts = message.split(":");

				UUID playerId = UUID.fromString(parts[0]);
				String name = parts[1];
				String serverIP = parts[2];

				removePlayerFromInstance(playerId, false);

				Instance instance = BMCManager.instanceManager.getFromIP(serverIP);
				if(instance == null) return;

				if(!(instance instanceof MinecraftInstance server)) return;

				server.addPlayer(playerId, name);
				RedisManager.get().updateInstance(server);
				updateGameServer(server);

 				QueueManager.releaseReservation(server.getUid(), playerId);
			}
		}, RedisChannel.INSTANCE_SWITCH.getRef())).start();
	}

	public void removePlayerFromInstance(UUID player, boolean removeProxy) {
		List<MinecraftInstance> instances = new ArrayList<>();
		GameServerManager manager = BMCManager.gameServerManager;

		manager.getGames().forEach(game -> instances.addAll(game.getInstances()));
		if(removeProxy && manager.getProxy() != null) {
			instances.addAll(manager.getProxy().getInstances());
		}

		for(Instance instance : instances) {
			if(!(instance instanceof MinecraftInstance minecraftInstance)) continue;

			if(minecraftInstance.hasPlayer(player)) {
				minecraftInstance.removePlayer(player);
				RedisManager.get().updateInstance(minecraftInstance);
			}
		}
	}

	public void updateGameServer(Instance instance) {
		GameServerManager gameServerManager = BMCManager.gameServerManager;
		GameServerWrapper<?> gameServer = gameServerManager.getGameServer(instance.getDeployment());
		if(gameServer != null) gameServer.fetchInstances();
	}
}
