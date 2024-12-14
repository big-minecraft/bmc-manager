package dev.kyriji.bmcmanager.controllers;

import dev.kyriji.bmcmanager.objects.Proxy;

public class ProxyManager {

	public Proxy proxyDeployment;

	public ProxyManager() {
		proxyDeployment = null;

		new Thread(() -> {
			while (true) {
				if (proxyDeployment != null) proxyDeployment.scale();
				try { Thread.sleep(1000); } catch (InterruptedException e) { e.printStackTrace(); }
			}
		}).start();
	}

	public Proxy getProxyDeployment() {
		return proxyDeployment;
	}

	public void updateProxy(Proxy proxy) {
		proxyDeployment = proxy;
	}
}
