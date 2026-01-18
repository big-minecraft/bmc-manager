package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.crd.GameServerSpec;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.interfaces.Scalable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class GameServerWrapper<T extends Instance> implements Scalable {

	protected GameServer gameServer;
	protected List<T> instances;
	private final String name;

	private QueueStrategy queueStrategy;
	private ScalingSettings scalingSettings;

	private long lastScaleUp = 0;
	private long lastScaleDown = 0;
	private boolean enabled = true;

	public GameServerWrapper(GameServer gameServer) {
		this.gameServer = gameServer;
		this.instances = new ArrayList<>();

		this.name = gameServer.getMetadata().getName();

		// Read queue strategy from CRD spec
		GameServerSpec.QueuingSpec queuing = gameServer.getSpec().getQueuing();
		this.queueStrategy = queuing != null
				? QueueStrategy.getStrategy(queuing.getQueueStrategy())
				: QueueStrategy.FILL;

		// Read scaling settings from CRD spec
		this.scalingSettings = new ScalingSettings(gameServer.getSpec().getScaling());

		// Read enabled state from Redis (default true if not set)
		String enabledStr = RedisManager.get().hget("deployment:" + name, "enabled");
		this.enabled = enabledStr == null || Boolean.parseBoolean(enabledStr);
	}

	public Type getInstanceType() {
		return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}

	public void fetchInstances() {
		int previousCount = this.instances.size();
		this.instances.clear();

		List<Instance> instances = RedisManager.get().scanAndDeserializeInstances("instance:*:" + name);
		this.instances.addAll((Collection<? extends T>) instances);

		// Log if instance count dropped unexpectedly (helps diagnose disappearing instances)
		if (previousCount > 0 && this.instances.isEmpty()) {
			System.err.println("WARNING: " + name + " went from " + previousCount + " instances to 0 after fetchInstances()!");
		}
	}

	public String getName() {
		return name;
	}

	public List<T> getInstances() {
		return new ArrayList<>(instances);
	}

	public GameServer getGameServer() {
		return gameServer;
	}

	public void setGameServer(GameServer gameServer) {
		this.gameServer = gameServer;

		// Refresh cached settings from updated CRD
		GameServerSpec.QueuingSpec queuing = gameServer.getSpec().getQueuing();
		this.queueStrategy = queuing != null
				? QueueStrategy.getStrategy(queuing.getQueueStrategy())
				: QueueStrategy.FILL;
		this.scalingSettings = new ScalingSettings(gameServer.getSpec().getScaling());

		System.out.println("Updated settings for " + name + ": " + scalingSettings);
	}

	public String getDeploymentType() {
		return gameServer.getSpec().getDeploymentType();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof GameServerWrapper<?> wrapper)) return false;
		return Objects.equals(getName(), wrapper.getName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getName(), name);
	}

	public QueueStrategy getQueueStrategy() {
		return queueStrategy;
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

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
