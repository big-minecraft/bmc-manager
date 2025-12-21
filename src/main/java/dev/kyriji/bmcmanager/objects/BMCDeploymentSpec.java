package dev.kyriji.bmcmanager.objects;

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;

public class BMCDeploymentSpec {
	private PodTemplateSpec template;

	public BMCDeploymentSpec(DeploymentSpec spec) {
		this.template = spec.getTemplate();
	}

	public BMCDeploymentSpec(StatefulSetSpec spec) {
		this.template = spec.getTemplate();
	}

	public PodTemplateSpec getTemplate() {
		return template;
	}

	public void setTemplate(PodTemplateSpec template) {
		this.template = template;
	}
}
