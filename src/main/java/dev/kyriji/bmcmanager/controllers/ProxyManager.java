package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.objects.Proxy;

public class ProxyManager {
	public Proxy proxy;

	public ProxyManager() {
		proxy = null;

		new Thread(() -> {
			while (true) {
				if (proxy != null) proxy.scale();
				try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
			}
		}).start();
	}

	public Proxy getProxy() {
		return proxy;
	}

	public void updateProxy(Proxy proxy) {
		this.proxy = proxy;
	}
}
