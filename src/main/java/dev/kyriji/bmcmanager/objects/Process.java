package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.crd.GameServer;

public class Process extends GameServerWrapper<Instance> {
	public Process(GameServer gameServer) {
		super(gameServer);
	}
}
