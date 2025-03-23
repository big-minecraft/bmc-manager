package dev.kyriji.bmcmanager.objects;

import com.google.gson.Gson;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.controllers.RedisManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.interfaces.Scalable;

import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public abstract class DeploymentWrapper<T extends Instance> implements Scalable {

	protected Deployment deployment;
	protected List<T> instances;
	private final String name;

	private final QueueStrategy queueStrategy;
	private final ScalingSettings scalingSettings;

	private long lastScaleUp = 0;
	private long lastScaleDown = 0;

	private final Gson gson;

	public DeploymentWrapper(Deployment deployment) {
		this.deployment = deployment;
		this.instances = new ArrayList<>();

		this.name = deployment.getMetadata().getName();

		this.queueStrategy = QueueStrategy.getStrategy(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.QUEUE_STRATEGY.getLabel()));

		this.scalingSettings = new ScalingSettings(deployment.getSpec().getTemplate().getMetadata().getLabels());

		this.gson = new Gson();
	}

	public Type getInstanceType() {
		return ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
	}

	public void scale() {
		if(!(getInstanceType() instanceof MinecraftInstance)) return;

		ScaleResult result = BMCManager.scalingManager.checkToScale((DeploymentWrapper<MinecraftInstance>) this);
		BMCManager.scalingManager.scale((DeploymentWrapper<MinecraftInstance>) this, result);
	}

	public void fetchInstances() {
		this.instances.clear();

		List<Instance> instances = RedisManager.get().scanAndDeserializeInstances("*:" + name);
		this.instances.addAll((Collection<? extends T>) instances);
	}

	public String getName() {
		return name;
	}

	public List<T> getInstances() {
		return new ArrayList<>(instances);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof DeploymentWrapper<?> wrapper)) return false;
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
