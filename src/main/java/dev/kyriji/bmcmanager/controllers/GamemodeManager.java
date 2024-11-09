package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.objects.Gamemode;

import java.util.ArrayList;
import java.util.List;

public class GamemodeManager {

	public List<Gamemode> gamemodes;

	public GamemodeManager() {
		this.gamemodes = new ArrayList<>();
	}

	public void registerGamemode(Gamemode gamemode) {
		System.out.println("Registering gamemode " + gamemode.getName());
		gamemodes.add(gamemode);
	}

	public void unregisterGamemode(Gamemode gamemode) {
		System.out.println("Unregistering gamemode " + gamemode.getName());
		gamemodes.remove(gamemode);
	}

	public List<Gamemode> getGamemodes() {
		return new ArrayList<>(gamemodes);
	}

	public Gamemode getGamemode(String name) {
		for (Gamemode gamemode : gamemodes) {
			if (gamemode.getName().equals(name)) return gamemode;
		}
		return null;
	}
}
