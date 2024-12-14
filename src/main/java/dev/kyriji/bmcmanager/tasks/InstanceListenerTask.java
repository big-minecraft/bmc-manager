package dev.kyriji.bmcmanager.tasks;

import com.google.gson.Gson;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.DeploymentManager;
import dev.kyriji.bmcmanager.controllers.QueueManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.objects.Deployment;
import dev.wiji.bigminecraftapi.enums.InstanceState;
import dev.wiji.bigminecraftapi.enums.RedisChannel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.UUID;

public class InstanceListenerTask {
	Gson gson = new Gson();

	public InstanceListenerTask() {
		DeploymentManager deploymentManager = BMCManager.deploymentManager;

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");

					String instanceIP = parts[0];
					String stateString = parts[1];

					UUID instanceUid = BMCManager.networkManager.getInstanceUUID(instanceIP);
					if(instanceUid == null) return;

					InstanceState state = InstanceState.valueOf(stateString);

					String instanceString = RedisManager.get().hgetAll("instances").get(instanceUid.toString());
					MinecraftInstance instance = gson.fromJson(instanceString, MinecraftInstance.class);

					if(instance == null) return;
					instance.setState(state);

					RedisManager.get().hset("instances", instanceUid.toString(), gson.toJson(instance));
					Deployment deployment = deploymentManager.getDeployment(instance.getDeployment());
					if(deployment == null) return;

					deployment.fetchInstances();
				}
			}, RedisChannel.INSTANCE_STATE_CHANGE.getRef());
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					List<Deployment> deployments = deploymentManager.getDeployments();
					List<Deployment> initialDeployments = deployments.stream()
							.filter(deployment -> deployment.isInitial() && !deployment.getInstances().isEmpty())
							.toList();

					if (initialDeployments.isEmpty()) return;

					Deployment deployment = initialDeployments.get((int) (Math.random() * initialDeployments.size()));
					MinecraftInstance instance = QueueManager.findInstance(deployment);

					if (instance != null) {
						RedisManager.get().publish(RedisChannel.INITIAL_INSTANCE_RESPONSE.getRef(), message + ":" + instance.getName());
					}
				}
			}, RedisChannel.REQUEST_INITIAL_INSTANCE.getRef());
		}).start();

		new Thread(() -> {
			RedisManager.get().subscribe(new JedisPubSub() {
				@Override
				public void onMessage(String channel, String message) {
					String[] parts = message.split(":");
					UUID playerId = UUID.fromString(parts[0]);
					String deploymentString = parts[1];

					Deployment deployment = deploymentManager.getDeployment(deploymentString);
					if(deployment == null) {
						//Used to send back an error to the proxy
						QueueManager.sendPlayerToInstance(playerId, null);
						return;
					}

					QueueManager.queuePlayer(playerId, deployment);
				}
			}, RedisChannel.QUEUE_PLAYER.getRef());
		}).start();
	}
}
