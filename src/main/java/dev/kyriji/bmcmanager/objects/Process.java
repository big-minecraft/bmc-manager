package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.wiji.bigminecraftapi.objects.Instance;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;

public class Process extends DeploymentWrapper<Instance> {
	public Process(Deployment deployment) {
		super(deployment);
	}
}
