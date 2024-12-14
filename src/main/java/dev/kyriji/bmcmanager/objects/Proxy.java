package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.interfaces.Scalable;
import dev.wiji.bigminecraftapi.BigMinecraftAPI;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.ArrayList;
import java.util.List;

public class Proxy implements Scalable {
	private final ScalingSettings scalingSettings;

	private final List<MinecraftInstance> instances;

	private long lastScaleUp = 0;
	private long lastScaleDown = 0;

	public Proxy(Deployment deployment) {
		this.scalingSettings = new ScalingSettings(deployment.getSpec().getTemplate().getMetadata().getLabels());

		this.instances = new ArrayList<>();
	}

	public void fetchInstances() {
		instances.clear();

		instances.addAll(BigMinecraftAPI.getNetworkManager().getProxies());
	}

	public void scale() {
		ScaleResult result = BMCManager.scalingManager.checkToScale(this);
		BMCManager.scalingManager.scale(this, result);
	}

	@Override
	public String getName() {
		return "proxy";
	}

	@Override
	public List<MinecraftInstance> getInstances() {
		return new ArrayList<>(instances);
	}

	@Override
	public ScalingSettings getScalingSettings() {
		return scalingSettings;
	}

	@Override
	public boolean isOnScaleUpCooldown() {
		return System.currentTimeMillis() - lastScaleUp < scalingSettings.scaleUpCooldown * 1000;
	}

	@Override
	public boolean isOnScaleDownCooldown() {
		return System.currentTimeMillis() - lastScaleDown < scalingSettings.scaleDownCooldown * 1000;
	}

	@Override
	public void setLastScaleUp(long lastScaleUp) {
		this.lastScaleUp = lastScaleUp;
	}

	@Override
	public void setLastScaleDown(long lastScaleDown) {
		this.lastScaleDown = lastScaleDown;
	}
}
