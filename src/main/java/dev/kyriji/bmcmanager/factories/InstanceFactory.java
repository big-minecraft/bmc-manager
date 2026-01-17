package dev.kyriji.bmcmanager.factories;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bmcmanager.BMCManager;
import dev.kyriji.bmcmanager.crd.GameServerSpec;
import dev.kyriji.bmcmanager.enums.DeploymentType;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import io.fabric8.kubernetes.api.model.Pod;

public class InstanceFactory {

	// Label used by PodBuilder to identify deployment type
	private static final String DEPLOYMENT_TYPE_LABEL = "kyriji.dev/deployment-type";

	public static Instance createFromPod(Pod pod) {
		String typeLabel = pod.getMetadata().getLabels().get(DEPLOYMENT_TYPE_LABEL);
		DeploymentType type = DeploymentType.getType(typeLabel);
		if(type == null) return null;

		String name = generateName(pod);
		String podName = pod.getMetadata().getName();
		String uid = pod.getMetadata().getUid();
		String ip = pod.getStatus().getPodIP();
		String deployment = pod.getMetadata().getLabels().get("app");

		Instance instance;
		instance = switch(type) {
			case SCALABLE, PERSISTENT, PROXY -> new MinecraftInstance(uid, name, podName, ip, deployment);
			case PROCESS -> new Instance(uid, name, podName, ip, deployment);
		};

		System.out.println(name);

		// Determine initial state based on requireStartupConfirmation
		InstanceState initialState = InstanceState.RUNNING;
		System.out.println("Looking for wrapper of deployment: " + deployment);
		GameServerWrapper<?> wrapper = BMCManager.gameServerManager.getGameServer(deployment);
		if (wrapper != null) {
			System.out.println("Found wrapper for deployment: " + deployment);
			GameServerSpec.QueuingSpec queuing = wrapper.getGameServer().getSpec().getQueuing();
			System.out.println("Startup confirmation required: " + (queuing != null ? queuing.getRequireStartupConfirmation() : "null"));
			if (queuing != null && Boolean.TRUE.equals(queuing.getRequireStartupConfirmation())) {
				initialState = InstanceState.STARTING;
				System.out.println("Setting initial state to STARTING for instance: " + name);
			}
		}
		instance.setState(initialState);

		return instance;
	}

	private static String generateName(Pod pod) {
		String deploymentName = pod.getMetadata().getLabels().get("app");
		String uid = pod.getMetadata().getUid();
		return deploymentName + "-" + uid.substring(0, 5);
	}
}
