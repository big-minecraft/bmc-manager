package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.crd.GameServerSpec;
import io.fabric8.kubernetes.api.model.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PodBuilder {
	private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	public Pod buildPod(GameServer gameServer) {
		String gameServerName = gameServer.getMetadata().getName();
		String namespace = gameServer.getMetadata().getNamespace();
		String podName = gameServerName + "-" + generateRandomSuffix(5);
		GameServerSpec spec = gameServer.getSpec();

		// Build container ports
		List<ContainerPort> containerPorts = buildContainerPorts(spec);

		// Build volume mounts
		List<VolumeMount> volumeMounts = buildVolumeMounts(spec);

		// Build volumes
		List<Volume> volumes = buildVolumes(gameServer);

		// Build the container
		Container container = new ContainerBuilder()
				.withName("server")
				.withImage(spec.getImage())
				.withCommand(spec.getCommand())
				.withEnv(spec.getEnv())
				.withResources(spec.getResources())
				.withPorts(containerPorts)
				.withVolumeMounts(volumeMounts)
				.build();

		// Build pod spec
		PodSpecBuilder podSpecBuilder = new PodSpecBuilder()
				.withContainers(container)
				.withVolumes(volumes)
				.withRestartPolicy("Never");

		// Add service account if specified
		if (spec.getServiceAccountName() != null && !spec.getServiceAccountName().isEmpty()) {
			podSpecBuilder.withServiceAccountName(spec.getServiceAccountName());
		}

		// Build the pod with owner reference
		return new io.fabric8.kubernetes.api.model.PodBuilder()
				.withNewMetadata()
					.withName(podName)
					.withNamespace(namespace)
					.withLabels(buildLabels(gameServer))
					.withOwnerReferences(buildOwnerReference(gameServer))
				.endMetadata()
				.withSpec(podSpecBuilder.build())
				.build();
	}

	private List<ContainerPort> buildContainerPorts(GameServerSpec spec) {
		List<ContainerPort> ports = new ArrayList<>();

		// Primary port
		if (spec.getPort() != null) {
			ports.add(new ContainerPortBuilder()
					.withName("minecraft")
					.withContainerPort(spec.getPort())
					.withProtocol("TCP")
					.build());
		}

		// Additional ports
		if (spec.getAdditionalPorts() != null) {
			for (GameServerSpec.AdditionalPort ap : spec.getAdditionalPorts()) {
				ports.add(new ContainerPortBuilder()
						.withName(ap.getName())
						.withContainerPort(ap.getPort())
						.withProtocol(ap.getProtocol() != null ? ap.getProtocol() : "TCP")
						.build());
			}
		}

		return ports;
	}

	private List<VolumeMount> buildVolumeMounts(GameServerSpec spec) {
		List<VolumeMount> mounts = new ArrayList<>();

		// Data volume mount (PVC)
		if (spec.getVolume() != null && spec.getVolume().getMountPath() != null) {
			mounts.add(new VolumeMountBuilder()
					.withName("data")
					.withMountPath(spec.getVolume().getMountPath())
					.build());
		}

		// Entrypoint ConfigMap mount
		mounts.add(new VolumeMountBuilder()
				.withName("entrypoint")
				.withMountPath("/entrypoint")
				.build());

		return mounts;
	}

	private List<Volume> buildVolumes(GameServer gameServer) {
		List<Volume> volumes = new ArrayList<>();
		String gameServerName = gameServer.getMetadata().getName();
		GameServerSpec spec = gameServer.getSpec();

		// PVC volume
		String pvcName = getPvcName(gameServerName, spec.getDeploymentType());
		if (pvcName != null) {
			volumes.add(new VolumeBuilder()
					.withName("data")
					.withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
							.withClaimName(pvcName)
							.build())
					.build());
		}

		// Entrypoint ConfigMap volume
		String configMapName = gameServerName + "-entrypoint";
		volumes.add(new VolumeBuilder()
				.withName("entrypoint")
				.withConfigMap(new ConfigMapVolumeSourceBuilder()
						.withName(configMapName)
						.withDefaultMode(0755)
						.build())
				.build());

		return volumes;
	}

	private String getPvcName(String gameServerName, String deploymentType) {
		if (deploymentType == null) return null;

		return switch (deploymentType.toLowerCase()) {
			case "scalable" -> "bmc-scalable-" + gameServerName;
			case "persistent" -> "bmc-persistent-" + gameServerName;
			case "proxy" -> "bmc-proxy";
			default -> null;
		};
	}

	private Map<String, String> buildLabels(GameServer gameServer) {
		return Map.of(
				"app", gameServer.getMetadata().getName(),
				"kyriji.dev/managed-by", "bmc-manager",
				"kyriji.dev/deployment-type", gameServer.getSpec().getDeploymentType()
		);
	}

	private OwnerReference buildOwnerReference(GameServer gameServer) {
		return new OwnerReferenceBuilder()
				.withApiVersion(gameServer.getApiVersion())
				.withKind(gameServer.getKind())
				.withName(gameServer.getMetadata().getName())
				.withUid(gameServer.getMetadata().getUid())
				.withController(true)
				.withBlockOwnerDeletion(true)
				.build();
	}

	private String generateRandomSuffix(int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
		}
		return sb.toString();
	}
}
