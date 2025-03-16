package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.DeploymentManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.DeploymentWrapper;
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
				RedisManager.get().hset(minecraftInstance.getDeployment(),
						minecraftInstance.getUid(),
						gson.toJson(minecraftInstance));

				updateDeployment(minecraftInstance);
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
				RedisManager.get().hset(minecraftInstance.getDeployment(),
						minecraftInstance.getUid(),
						gson.toJson(minecraftInstance));

				removePlayerFromInstance(playerId, true);
				updateDeployment(minecraftInstance);
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
				RedisManager.get().hset(server.getDeployment(), server.getUid(), gson.toJson(server));
				updateDeployment(server);
			}
		}, RedisChannel.INSTANCE_SWITCH.getRef())).start();
	}

	public void removePlayerFromInstance(UUID player, boolean removeProxy) {
		List<MinecraftInstance> instances = new ArrayList<>();
		DeploymentManager manager = BMCManager.deploymentManager;

		manager.getGames().forEach(game -> instances.addAll(game.getInstances()));
		if(removeProxy) instances.addAll(manager.getProxy().getInstances());

		for(Instance instance : instances) {
			if(!(instance instanceof MinecraftInstance minecraftInstance)) continue;

			if(minecraftInstance.hasPlayer(player)) {
				minecraftInstance.removePlayer(player);
				RedisManager.get().hset(instance.getDeployment(), instance.getUid(), gson.toJson(instance));
			}
		}
	}

	public void updateDeployment(Instance instance) {
		DeploymentManager deploymentManager = BMCManager.deploymentManager;
		DeploymentWrapper<?> deployment = deploymentManager.getDeployment(instance.getDeployment());
		if(deployment != null) deployment.fetchInstances();
	}
}
