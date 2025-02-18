package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.QueueStrategy;
import dev.kyriji.bmcmanager.enums.ScaleResult;
import dev.kyriji.bmcmanager.interfaces.Scalable;
import dev.wiji.bigminecraftapi.BigMinecraftAPI;
import dev.wiji.bigminecraftapi.objects.Instance;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Game extends DeploymentWrapper<MinecraftInstance> {
	private final boolean isInitial;

	public Game(Deployment deployment) {
		super(deployment);

		this.isInitial = Boolean.parseBoolean(deployment.getSpec().getTemplate().getMetadata().getLabels()
				.get(DeploymentLabel.INITIAL_SERVER.getLabel()));
	}

	public boolean isInitial() {
		return isInitial;
	}
}
