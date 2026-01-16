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

	private final QueueStrategy queueStrategy;
	private final ScalingSettings scalingSettings;

	private long lastScaleUp = 0;
	private long lastScaleDown = 0;

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
	}

	public Type getInstanceType() {
		return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}

	public void fetchInstances() {
		this.instances.clear();

		List<Instance> instances = RedisManager.get().scanAndDeserializeInstances("instance:*:" + name);
		this.instances.addAll((Collection<? extends T>) instances);
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
}
