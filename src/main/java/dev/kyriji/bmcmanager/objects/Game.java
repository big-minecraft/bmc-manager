package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;

public class Game extends DeploymentWrapper<MinecraftInstance> {
	private final boolean isInitial;

	public Game(BMCDeployment deployment) {
		super(deployment);

		this.isInitial = Boolean.parseBoolean(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.INITIAL_SERVER.getLabel()));
	}

	public boolean isInitial() {
		return isInitial;
	}
}
