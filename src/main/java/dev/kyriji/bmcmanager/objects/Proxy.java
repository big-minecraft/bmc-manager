package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.interfaces.Scalable;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.apps.Deployment;

public class Proxy extends DeploymentWrapper<MinecraftInstance> implements Scalable {

	public Proxy(Deployment deployment) {
		super(deployment);
	}
}
