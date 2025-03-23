package dev.kyriji.bmcmanager.factories;

import dev.kyriji.bigminecraftapi.enums.InstanceState;
import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.enums.DeploymentType;
import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;

public class InstanceFactory {

	public static Instance createFromPod(Pod pod) {
		DeploymentType type = DeploymentType.getType(pod.getMetadata().getLabels().get(DeploymentLabel.DEPLOYMENT_TYPE.getLabel()));
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

		if(pod.getMetadata().getLabels().containsKey(DeploymentLabel.REQUIRE_STARTUP_CONFIRMATION.getLabel())) {
			boolean requiresStartupConfirmation = Boolean.parseBoolean(pod.getMetadata().getLabels().get(DeploymentLabel.REQUIRE_STARTUP_CONFIRMATION.getLabel()));
			instance.setState(requiresStartupConfirmation ? InstanceState.STARTING: InstanceState.RUNNING);
		}

		return instance;
	}

	private static String generateName(Pod pod) {
		String deploymentName = pod.getMetadata().getLabels().get("app");
		String uid = pod.getMetadata().getUid();
		return deploymentName + "-" + uid.substring(0, 5);
	}
}