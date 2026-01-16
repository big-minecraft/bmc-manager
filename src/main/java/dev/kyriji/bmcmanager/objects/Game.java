package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.crd.GameServerSpec;

public class Game extends GameServerWrapper<MinecraftInstance> {
	private final boolean isInitial;

	public Game(GameServer gameServer) {
		super(gameServer);

		GameServerSpec.QueuingSpec queuing = gameServer.getSpec().getQueuing();
		this.isInitial = queuing != null && Boolean.TRUE.equals(queuing.getInitialServer());
	}

	public boolean isInitial() {
		return isInitial;
	}
}
