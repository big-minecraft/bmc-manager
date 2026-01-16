package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.enums.DeploymentType;
import dev.kyriji.bmcmanager.objects.Game;
import dev.kyriji.bmcmanager.objects.GameServerWrapper;
import dev.kyriji.bmcmanager.objects.Process;
import dev.kyriji.bmcmanager.objects.Proxy;

import java.util.ArrayList;
import java.util.List;

public class GameServerManager {
	public List<GameServerWrapper<?>> gameServers;

	public GameServerManager() {
		this.gameServers = new ArrayList<>();
	}

	public void registerGameServer(GameServerWrapper<?> gameServer) {
		System.out.println("Registering GameServer " + gameServer.getName());
		gameServers.add(gameServer);
	}

	public void unregisterGameServer(GameServerWrapper<?> gameServer) {
		System.out.println("Unregistering GameServer " + gameServer.getName());
		gameServers.remove(gameServer);
	}

	public void updateGameServer(GameServerWrapper<?> gameServer) {
		GameServerWrapper<?> wrapper = getGameServer(gameServer.getName());
		if (wrapper == null) return;
		gameServers.removeIf(gs -> gs.getName().equals(gameServer.getName()));
		gameServers.add(gameServer);
	}

	public List<GameServerWrapper<?>> getGameServers() {
		return new ArrayList<>(gameServers);
	}

	public GameServerWrapper<?> getGameServer(String name) {
		for (GameServerWrapper<?> gameServer : gameServers) {
			if (gameServer.getName().equals(name)) return gameServer;
		}
		return null;
	}

	public List<Game> getGames() {
		List<Game> games = new ArrayList<>();
		for (GameServerWrapper<?> gameServer : gameServers) {
			if (gameServer instanceof Game) {
				games.add((Game) gameServer);
			}
		}
		return games;
	}

	public Game getGame(String name) {
		for (Game game : getGames()) {
			if (game.getName().equals(name)) return game;
		}
		return null;
	}

	public Proxy getProxy() {
		for (GameServerWrapper<?> gameServer : gameServers) {
			if (gameServer instanceof Proxy) {
				return (Proxy) gameServer;
			}
		}
		return null;
	}

	public GameServerWrapper<?> createWrapper(GameServer gameServer) {
		String typeString = gameServer.getSpec().getDeploymentType();
		DeploymentType type = DeploymentType.getType(typeString);

		if (type == null) {
			System.err.println("Unknown deployment type: " + typeString);
			return null;
		}

		return switch (type) {
			case SCALABLE, PERSISTENT -> new Game(gameServer);
			case PROXY -> new Proxy(gameServer);
			case PROCESS -> new Process(gameServer);
		};
	}
}
