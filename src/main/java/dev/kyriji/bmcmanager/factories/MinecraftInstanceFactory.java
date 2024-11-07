package dev.kyriji.bmcmanager.factories;

import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;
import io.fabric8.kubernetes.api.model.Pod;

public class MinecraftInstanceFactory {

	public static MinecraftInstance createFromPod(Pod pod) {
		String name = generateName(pod);
		String podName = pod.getMetadata().getName();
		String uid = pod.getMetadata().getUid();
		String ip = pod.getStatus().getPodIP();
		String gamemode = pod.getMetadata().getLabels().get("app");

		String initialServerTag = pod.getMetadata().getLabels().get(DeploymentLabel.INITIAL_SERVER.getLabel());
		boolean initialServer = initialServerTag != null && initialServerTag.equals("true");

		return new MinecraftInstance(uid, name, podName, ip, gamemode, initialServer);
	}

	private static String generateName(Pod pod) {
		String deploymentName = pod.getMetadata().getLabels().get("app");
		String uid = pod.getMetadata().getUid();
		return deploymentName + "-" + uid.substring(0, 5);
	}
}