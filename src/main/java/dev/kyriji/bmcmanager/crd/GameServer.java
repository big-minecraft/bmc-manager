package dev.kyriji.bmcmanager.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("kyriji.dev")
@Version("v1alpha1")
@Kind("GameServer")
@Plural("gameservers")
@Singular("gameserver")
public class GameServer extends CustomResource<GameServerSpec, Void> implements Namespaced {
	// No status field - Redis is the source of truth for instance state
}
