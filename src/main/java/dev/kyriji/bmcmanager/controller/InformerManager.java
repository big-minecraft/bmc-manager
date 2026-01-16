package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bmcmanager.crd.GameServer;
import dev.kyriji.bmcmanager.objects.ReconcileRequest;
import dev.kyriji.bmcmanager.objects.ReconcileResult;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class InformerManager {
	private final KubernetesClient client;
	private final SharedInformerFactory informerFactory;
	private final ReconciliationQueue queue;
	private final GameServerReconciler reconciler;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread reconciliationThread;
	private SharedIndexInformer<GameServer> gameServerInformer;

	public InformerManager(KubernetesClient client, ReconciliationQueue queue) {
		this.client = client;
		this.queue = queue;
		this.reconciler = new GameServerReconciler(client);
		this.informerFactory = client.informers();
	}

	public void setupInformers() {
		// Setup GameServer CRD informer
		gameServerInformer = informerFactory.sharedIndexInformerFor(
			GameServer.class,
			10 * 60 * 1000L // 10 minute resync period
		);

		gameServerInformer.addEventHandler(new ResourceEventHandler<GameServer>() {
			@Override
			public void onAdd(GameServer gameServer) {
				System.out.println("GameServer added: " + gameServer.getMetadata().getName());
				queue.enqueue(ReconcileRequest.forGameServer(gameServer));
			}

			@Override
			public void onUpdate(GameServer oldGameServer, GameServer newGameServer) {
				System.out.println("GameServer updated: " + newGameServer.getMetadata().getName());
				queue.enqueue(ReconcileRequest.forGameServer(newGameServer));
			}

			@Override
			public void onDelete(GameServer gameServer, boolean deletedFinalStateUnknown) {
				System.out.println("GameServer deleted: " + gameServer.getMetadata().getName());
				// Pods will be garbage collected via owner references
				// Optionally cleanup any in-memory state here
			}
		});

		System.out.println("Informers configured for GameServer CRDs");
	}

	public void start() {
		System.out.println("Starting informers...");
		informerFactory.startAllRegisteredInformers();

		// Wait for informers to sync
		System.out.println("Waiting for informers to sync...");
		int maxWaitSeconds = 30;
		int waitedSeconds = 0;
		while (!gameServerInformer.hasSynced() && waitedSeconds < maxWaitSeconds) {
			try {
				Thread.sleep(1000);
				waitedSeconds++;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		if (waitedSeconds >= maxWaitSeconds) {
			System.err.println("Warning: Informers did not sync within " + maxWaitSeconds + " seconds");
		} else {
			System.out.println("Informers synced successfully in " + waitedSeconds + " seconds");
		}

		// Start reconciliation loop
		startReconciliationLoop();
	}

	private void startReconciliationLoop() {
		running.set(true);
		reconciliationThread = new Thread(() -> {
			System.out.println("Reconciliation loop started!");
			while (running.get()) {
				try {
					ReconcileRequest request = queue.dequeue();

					// Process the reconciliation request
					ReconcileResult result = reconciler.reconcile(request);

					// Only mark complete if NOT requeueing
					// This prevents duplicate enqueues during the requeue delay window
					if (!result.shouldRequeue()) {
						queue.markComplete(request);
					} else {
						queue.requeue(request, result.getRequeueAfterMs());
					}

				} catch (InterruptedException e) {
					// Thread interrupted, exit loop
					Thread.currentThread().interrupt();
					break;
				} catch (Exception e) {
					System.err.println("Error in reconciliation loop: " + e.getMessage());
					e.printStackTrace();
				}
			}
			System.out.println("Reconciliation loop stopped");
		}, "reconciliation-loop");

		reconciliationThread.setDaemon(false);
		reconciliationThread.start();
	}

	public void shutdown() {
		System.out.println("Shutting down InformerManager...");
		running.set(false);

		if (reconciliationThread != null) {
			reconciliationThread.interrupt();
			try {
				reconciliationThread.join(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		informerFactory.stopAllRegisteredInformers();
		queue.shutdown();
		System.out.println("InformerManager shutdown complete");
	}
}
