package dev.kyriji.bmcmanager.objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;

public class BMCDeployment implements HasMetadata {
	private final BMCDeploymentSpec spec;
	private final ObjectMeta metadata;

	public BMCDeployment(Deployment deployment) {
		this.spec = new BMCDeploymentSpec(deployment.getSpec());
		this.metadata = deployment.getMetadata();
	}

	public BMCDeployment(StatefulSet statefulSet) {
		this.spec = new BMCDeploymentSpec(statefulSet.getSpec());
		this.metadata = statefulSet.getMetadata();
	}

	@Override
	public ObjectMeta getMetadata() {
		return this.metadata;
	}

	@Override
	public void setMetadata(ObjectMeta objectMeta) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void setApiVersion(String s) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public BMCDeploymentSpec getSpec() {
		return this.spec;
	}
}
