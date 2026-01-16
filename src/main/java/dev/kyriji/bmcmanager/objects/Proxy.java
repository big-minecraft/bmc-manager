package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bigminecraftapi.objects.MinecraftInstance;
import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.interfaces.Scalable;

public class Proxy extends GameServerWrapper<MinecraftInstance> implements Scalable {

	public Proxy(GameServer gameServer) {
		super(gameServer);
	}
}
