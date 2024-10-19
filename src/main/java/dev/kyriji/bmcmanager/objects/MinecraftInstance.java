package dev.kyriji.bmcmanager.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MinecraftInstance {

	private final String uid;
	private final String name;
	private final String ip;
	private final List<UUID> players;

	public MinecraftInstance(String uid, String name, String ip) {
		this.uid = uid;
		this.name = name;
		this.ip = ip;

		this.players = new ArrayList<>();
	}

	public String getUid() {
		return uid;
	}

	public String getName() {
		return name;
	}

	public String getIp() {
		return ip;
	}

	public List<UUID> getPlayers() {
		return players;
	}

}
