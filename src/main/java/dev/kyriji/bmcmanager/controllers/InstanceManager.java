package dev.kyriji.bmcmanager.controllers;

import com.google.gson.Gson;
import dev.kyriji.bigminecraftapi.enums.RedisChannel;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.BMCManager;


import java.util.ArrayList;
import java.util.List;

public class InstanceManager {
	private final Gson gson = new Gson();

	public InstanceManager() {
		clearExistingData();
	}

	private void clearExistingData() {
		RedisManager.get().clear();
		RedisManager.get().publish(RedisChannel.INSTANCE_MODIFIED.getRef(), "");
	}

	public void registerInstance(Instance instance) {
		System.out.println("Registering instance: " + instance.getUid());
		RedisManager.get().updateInstance(instance);
		RedisManager.get().publish(RedisChannel.INSTANCE_MODIFIED.getRef(), gson.toJson(instance));
	}

	public void unregisterInstance(String deploymentName, String uid) {
		String key = "instance:" + uid + ":" + deploymentName;
		RedisManager.get().hdelAll(key);
		RedisManager.get().publish(RedisChannel.INSTANCE_MODIFIED.getRef(), "");
	}


	public List<Instance> getInstances() {
		List<Instance> instances = new ArrayList<>();
		BMCManager.deploymentManager.getDeployments().forEach(game -> instances.addAll(game.getInstances()));
		return instances;
	}

	public Instance getFromIP(String ip) {
		for (Instance instance : getInstances()) {
			if (instance.getIp().equals(ip)) return instance;
		}
		return null;
	}
}