package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bmcmanager.enums.ResourceType;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.Objects;

public class ReconcileRequest {
	private final String namespace;
	private final String name;
	private final ResourceType resourceType;
	private final long timestamp;

	public ReconcileRequest(String namespace, String name, ResourceType resourceType) {
		this.namespace = namespace;
		this.name = name;
		this.resourceType = resourceType;
		this.timestamp = System.currentTimeMillis();
	}

	public static ReconcileRequest forDeployment(HasMetadata resource) {
		return new ReconcileRequest(
			resource.getMetadata().getNamespace(),
			resource.getMetadata().getName(),
			ResourceType.DEPLOYMENT
		);
	}

	public static ReconcileRequest forStatefulSet(HasMetadata resource) {
		return new ReconcileRequest(
			resource.getMetadata().getNamespace(),
			resource.getMetadata().getName(),
			ResourceType.STATEFULSET
		);
	}

	public String getNamespace() {
		return namespace;
	}

	public String getName() {
		return name;
	}

	public ResourceType getResourceType() {
		return resourceType;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ReconcileRequest that)) return false;
		return Objects.equals(namespace, that.namespace) &&
			   Objects.equals(name, that.name) &&
			   resourceType == that.resourceType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(namespace, name, resourceType);
	}

	@Override
	public String toString() {
		return "ReconcileRequest{" +
			   "namespace='" + namespace + '\'' +
			   ", name='" + name + '\'' +
			   ", resourceType=" + resourceType +
			   '}';
	}
}
