package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.objects.Gamemode;

import java.util.ArrayList;
import java.util.List;

public class GamemodeManager {
	public static List<Gamemode> gamemodes = new ArrayList<>();

	public static void registerGamemode(Gamemode gamemode) {
		gamemodes.add(gamemode);
	}

	public static void unregisterGamemode(Gamemode gamemode) {
		gamemodes.remove(gamemode);
	}

	public static List<Gamemode> getGamemodes() {
		return gamemodes;
	}

	public static Gamemode getGamemode(String name) {
		for (Gamemode gamemode : gamemodes) {
			if (gamemode.getName().equals(name)) return gamemode;
		}
		return null;
	}
}
