package dev.kyriji.bmcmanager.crd;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

import java.util.List;

public class GameServerSpec {
	private String deploymentType;
	private String image;
	private List<String> command;
	private Integer port;
	private List<AdditionalPort> additionalPorts;
	private List<EnvVar> env;
	private ResourceRequirements resources;
	private VolumeSpec volume;
	private RedisConfig redis;
	private ScalingSpec scaling;
	private QueuingSpec queuing;
	private String serviceAccountName;

	// Getters and Setters
	public String getDeploymentType() {
		return deploymentType;
	}

	public void setDeploymentType(String deploymentType) {
		this.deploymentType = deploymentType;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public List<String> getCommand() {
		return command;
	}

	public void setCommand(List<String> command) {
		this.command = command;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public List<AdditionalPort> getAdditionalPorts() {
		return additionalPorts;
	}

	public void setAdditionalPorts(List<AdditionalPort> additionalPorts) {
		this.additionalPorts = additionalPorts;
	}

	public List<EnvVar> getEnv() {
		return env;
	}

	public void setEnv(List<EnvVar> env) {
		this.env = env;
	}

	public ResourceRequirements getResources() {
		return resources;
	}

	public void setResources(ResourceRequirements resources) {
		this.resources = resources;
	}

	public VolumeSpec getVolume() {
		return volume;
	}

	public void setVolume(VolumeSpec volume) {
		this.volume = volume;
	}

	public RedisConfig getRedis() {
		return redis;
	}

	public void setRedis(RedisConfig redis) {
		this.redis = redis;
	}

	public ScalingSpec getScaling() {
		return scaling;
	}

	public void setScaling(ScalingSpec scaling) {
		this.scaling = scaling;
	}

	public QueuingSpec getQueuing() {
		return queuing;
	}

	public void setQueuing(QueuingSpec queuing) {
		this.queuing = queuing;
	}

	public String getServiceAccountName() {
		return serviceAccountName;
	}

	public void setServiceAccountName(String serviceAccountName) {
		this.serviceAccountName = serviceAccountName;
	}

	// Nested Classes
	public static class AdditionalPort {
		private String name;
		private Integer port;
		private String protocol;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getPort() {
			return port;
		}

		public void setPort(Integer port) {
			this.port = port;
		}

		public String getProtocol() {
			return protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}
	}

	public static class VolumeSpec {
		private String mountPath;
		private String storageClass;
		private String size;

		public String getMountPath() {
			return mountPath;
		}

		public void setMountPath(String mountPath) {
			this.mountPath = mountPath;
		}

		public String getStorageClass() {
			return storageClass;
		}

		public void setStorageClass(String storageClass) {
			this.storageClass = storageClass;
		}

		public String getSize() {
			return size;
		}

		public void setSize(String size) {
			this.size = size;
		}
	}

	public static class RedisConfig {
		private String host;
		private String port;

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public String getPort() {
			return port;
		}

		public void setPort(String port) {
			this.port = port;
		}
	}

	public static class ScalingSpec {
		private String strategy;
		private Integer maxPlayers;
		private Integer minInstances;
		private Integer maxInstances;
		private Integer scaleUpThreshold;
		private Integer scaleDownThreshold;
		private Integer scaleUpCooldown;
		private Integer scaleDownCooldown;
		private Integer scaleUpLimit;
		private Integer scaleDownLimit;

		public String getStrategy() {
			return strategy;
		}

		public void setStrategy(String strategy) {
			this.strategy = strategy;
		}

		public Integer getMaxPlayers() {
			return maxPlayers;
		}

		public void setMaxPlayers(Integer maxPlayers) {
			this.maxPlayers = maxPlayers;
		}

		public Integer getMinInstances() {
			return minInstances;
		}

		public void setMinInstances(Integer minInstances) {
			this.minInstances = minInstances;
		}

		public Integer getMaxInstances() {
			return maxInstances;
		}

		public void setMaxInstances(Integer maxInstances) {
			this.maxInstances = maxInstances;
		}

		public Integer getScaleUpThreshold() {
			return scaleUpThreshold;
		}

		public void setScaleUpThreshold(Integer scaleUpThreshold) {
			this.scaleUpThreshold = scaleUpThreshold;
		}

		public Integer getScaleDownThreshold() {
			return scaleDownThreshold;
		}

		public void setScaleDownThreshold(Integer scaleDownThreshold) {
			this.scaleDownThreshold = scaleDownThreshold;
		}

		public Integer getScaleUpCooldown() {
			return scaleUpCooldown;
		}

		public void setScaleUpCooldown(Integer scaleUpCooldown) {
			this.scaleUpCooldown = scaleUpCooldown;
		}

		public Integer getScaleDownCooldown() {
			return scaleDownCooldown;
		}

		public void setScaleDownCooldown(Integer scaleDownCooldown) {
			this.scaleDownCooldown = scaleDownCooldown;
		}

		public Integer getScaleUpLimit() {
			return scaleUpLimit;
		}

		public void setScaleUpLimit(Integer scaleUpLimit) {
			this.scaleUpLimit = scaleUpLimit;
		}

		public Integer getScaleDownLimit() {
			return scaleDownLimit;
		}

		public void setScaleDownLimit(Integer scaleDownLimit) {
			this.scaleDownLimit = scaleDownLimit;
		}
	}

	public static class QueuingSpec {
		private String initialServer;
		private Boolean requireStartupConfirmation;
		private String queueStrategy;

		public String getInitialServer() {
			return initialServer;
		}

		public void setInitialServer(String initialServer) {
			this.initialServer = initialServer;
		}

		public Boolean getRequireStartupConfirmation() {
			return requireStartupConfirmation;
		}

		public void setRequireStartupConfirmation(Boolean requireStartupConfirmation) {
			this.requireStartupConfirmation = requireStartupConfirmation;
		}

		public String getQueueStrategy() {
			return queueStrategy;
		}

		public void setQueueStrategy(String queueStrategy) {
			this.queueStrategy = queueStrategy;
		}
	}
}
