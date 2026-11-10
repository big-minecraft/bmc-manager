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

		// Artifact mode: pull the published version into the emptyDir before the
		// server starts, instead of reading the shared volume.
		Container fetcher = buildArtifactFetcher(spec);
		if (fetcher != null) podSpecBuilder.withInitContainers(fetcher);

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

	/**
	 * Init container that pulls a published artifact into the data volume.
	 *
	 * Returns null when the spec carries no artifact, which leaves the pod
	 * exactly as it was before -- mounting the deployment's shared volume.
	 *
	 * mc mirror rather than a tarball: the mc image ships no tar, and mirroring
	 * keeps the version prefix browsable in the bucket.
	 */
	private Container buildArtifactFetcher(GameServerSpec spec) {
		GameServerSpec.ArtifactSpec artifact = spec.getArtifact();
		if (artifact == null || artifact.getVersion() == null || artifact.getVersion().isEmpty()) return null;
		if (spec.getVolume() == null || spec.getVolume().getMountPath() == null) return null;

		String mountPath = spec.getVolume().getMountPath();
		String remote = String.format("store/%s/deployments/%s/%s/",
				artifact.getBucket(), artifact.getDeployment(), artifact.getVersion());

		String script = String.join("\n",
				"set -e",
				"mc alias set store \"$ARTIFACT_ENDPOINT\" \"$ARTIFACT_ACCESS_KEY\" \"$ARTIFACT_SECRET_KEY\"",
				"echo \"Fetching " + artifact.getDeployment() + " " + artifact.getVersion() + "...\"",
				"mc mirror --overwrite \"" + remote + "\" " + mountPath,
				"echo \"Fetched " + artifact.getVersion() + "\"");

		return new ContainerBuilder()
				.withName("fetch-artifact")
				.withImage("docker.io/bitnamilegacy/minio:2025.7.23-debian-12-r3")
				.withCommand("/bin/sh", "-c", script)
				.withEnv(
						new EnvVarBuilder().withName("ARTIFACT_ENDPOINT").withValue(artifact.getEndpoint()).build(),
						new EnvVarBuilder().withName("ARTIFACT_ACCESS_KEY")
								.withNewValueFrom().withNewSecretKeyRef("artifactAccessKey", "bmc-secrets", false).endValueFrom().build(),
						new EnvVarBuilder().withName("ARTIFACT_SECRET_KEY")
								.withNewValueFrom().withNewSecretKeyRef("artifactSecretKey", "bmc-secrets", false).endValueFrom().build()
				)
				.withVolumeMounts(new VolumeMountBuilder()
						.withName("data")
						.withMountPath(mountPath)
						.build())
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
			// readOnly is ignored in artifact mode: the init container has to
			// write into this volume, and nothing else shares it.
			boolean usesArtifact = spec.getArtifact() != null
					&& spec.getArtifact().getVersion() != null
					&& !spec.getArtifact().getVersion().isEmpty();
			boolean readOnly = !usesArtifact && Boolean.TRUE.equals(spec.getVolume().getReadOnly());

			mounts.add(new VolumeMountBuilder()
					.withName("data")
					.withMountPath(spec.getVolume().getMountPath())
					.withReadOnly(readOnly)
					.build());
		}

		// Entrypoint ConfigMap mount - not used by process deployments
		if (!"process".equalsIgnoreCase(spec.getDeploymentType())) {
			mounts.add(new VolumeMountBuilder()
					.withName("entrypoint")
					.withMountPath("/entrypoint")
					.build());
		}

		return mounts;
	}

	private List<Volume> buildVolumes(GameServer gameServer) {
		List<Volume> volumes = new ArrayList<>();
		String gameServerName = gameServer.getMetadata().getName();
		GameServerSpec spec = gameServer.getSpec();

		// Data volume: an emptyDir the init container fills from object storage
		// when an artifact is set, otherwise the deployment's shared PVC.
		//
		// This is the change that lets a scale-up stop contending on one
		// filesystem. The PVC still exists either way -- file sessions and the
		// publish Job mount it -- these pods just no longer do.
		boolean usesArtifact = spec.getArtifact() != null
				&& spec.getArtifact().getVersion() != null
				&& !spec.getArtifact().getVersion().isEmpty();

		if (usesArtifact) {
			volumes.add(new VolumeBuilder()
					.withName("data")
					.withEmptyDir(new EmptyDirVolumeSourceBuilder().build())
					.build());
		} else {
			String pvcName = getPvcName(gameServerName, spec.getDeploymentType());
			if (pvcName != null) {
				volumes.add(new VolumeBuilder()
						.withName("data")
						.withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
								.withClaimName(pvcName)
								.build())
						.build());
			}
		}

		// Entrypoint ConfigMap volume - not used by process deployments
		if (!"process".equalsIgnoreCase(spec.getDeploymentType())) {
			String configMapName = gameServerName + "-entrypoint";
			volumes.add(new VolumeBuilder()
					.withName("entrypoint")
					.withConfigMap(new ConfigMapVolumeSourceBuilder()
							.withName(configMapName)
							.withDefaultMode(0755)
							.build())
					.build());
		}

		return volumes;
	}

	private String getPvcName(String gameServerName, String deploymentType) {
		if (deploymentType == null) return null;

		return switch (deploymentType.toLowerCase()) {
			case "scalable" -> "bmc-scalable-" + gameServerName;
			case "persistent" -> "bmc-persistent-" + gameServerName;
			case "proxy" -> "bmc-proxy";
			case "process" -> "bmc-process-" + gameServerName;
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
