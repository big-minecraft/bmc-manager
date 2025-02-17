package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.objects.Game;

import java.util.ArrayList;
import java.util.List;

public class GameManager {
	public List<Game> games;

	public GameManager() {
		this.games = new ArrayList<>();

		new Thread(() -> {
			while (true) {
				for(Game game : games) game.scale();
				try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
			}
		}).start();
	}

	public void registerGame(Game game) {
		System.out.println("Registering game " + game.getName());
		games.add(game);
	}

	public void unregisterGame(Game game) {
		System.out.println("Unregistering game " + game.getName());
		games.remove(game);
	}

	public List<Game> getGames() {
		return new ArrayList<>(games);
	}

	public Game getGame(String name) {
		for (Game game : games) if (game.getName().equals(name)) return game;
		return null;
	}
}
