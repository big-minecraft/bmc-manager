package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bmcmanager.enums.DeploymentLabel;
import dev.kyriji.bmcmanager.objects.ReconcileRequest;
import dev.kyriji.bmcmanager.objects.ReconcileResult;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class InformerManager {
	// ============ DEBUG FLAG ============
	// Set to false to disable all scaling debug output
	private static final boolean DEBUG_SCALING = true;
	// ====================================

	private final KubernetesClient client;
	private final SharedInformerFactory informerFactory;
	private final ReconciliationQueue queue;
	private final DeploymentReconciler reconciler;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private Thread reconciliationThread;

	public InformerManager(KubernetesClient client, ReconciliationQueue queue) {
		this.client = client;
		this.queue = queue;
		this.reconciler = new DeploymentReconciler(client);
		this.informerFactory = client.informers();
	}

	public void setupInformers() {
		// Setup Deployment informer
		SharedIndexInformer<Deployment> deploymentInformer = informerFactory.sharedIndexInformerFor(
			Deployment.class,
			10 * 60 * 1000L // 10 minute resync period
		);

		deploymentInformer.addEventHandler(new ResourceEventHandler<Deployment>() {
			@Override
			public void onAdd(Deployment deployment) {
				if (DEBUG_SCALING) {
					System.out.println("\n[INFORMER] Deployment ADDED: " + deployment.getMetadata().getNamespace() + "/" + deployment.getMetadata().getName());
				}
				if (shouldReconcile(deployment)) {
					if (DEBUG_SCALING) {
						System.out.println("  -> Enqueueing for reconciliation");
					}
					queue.enqueue(ReconcileRequest.forDeployment(deployment));
				} else if (DEBUG_SCALING) {
					System.out.println("  -> Skipped (no server-discovery label)");
				}
			}

			@Override
			public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
				if (DEBUG_SCALING) {
					System.out.println("\n[INFORMER] Deployment UPDATED: " + newDeployment.getMetadata().getNamespace() + "/" + newDeployment.getMetadata().getName());
				}
				if (shouldReconcile(newDeployment)) {
					if (DEBUG_SCALING) {
						System.out.println("  -> Enqueueing for reconciliation");
					}
					queue.enqueue(ReconcileRequest.forDeployment(newDeployment));
				} else if (DEBUG_SCALING) {
					System.out.println("  -> Skipped (no server-discovery label)");
				}
			}

			@Override
			public void onDelete(Deployment deployment, boolean deletedFinalStateUnknown) {
				if (DEBUG_SCALING) {
					System.out.println("\n[INFORMER] Deployment DELETED: " + deployment.getMetadata().getNamespace() + "/" + deployment.getMetadata().getName());
				}
				// Optional: cleanup logic if needed
			}
		});

		// Setup StatefulSet informer
		SharedIndexInformer<StatefulSet> statefulSetInformer = informerFactory.sharedIndexInformerFor(
			StatefulSet.class,
			10 * 60 * 1000L // 10 minute resync period
		);

		statefulSetInformer.addEventHandler(new ResourceEventHandler<StatefulSet>() {
			@Override
			public void onAdd(StatefulSet statefulSet) {
				if (DEBUG_SCALING) {
					System.out.println("\n[INFORMER] StatefulSet ADDED: " + statefulSet.getMetadata().getNamespace() + "/" + statefulSet.getMetadata().getName());
				}
				if (shouldReconcile(statefulSet)) {
					if (DEBUG_SCALING) {
						System.out.println("  -> Enqueueing for reconciliation");
					}
					queue.enqueue(ReconcileRequest.forStatefulSet(statefulSet));
				} else if (DEBUG_SCALING) {
					System.out.println("  -> Skipped (no server-discovery label)");
				}
			}

			@Override
			public void onUpdate(StatefulSet oldStatefulSet, StatefulSet newStatefulSet) {
				if (DEBUG_SCALING) {
					System.out.println("\n[INFORMER] StatefulSet UPDATED: " + newStatefulSet.getMetadata().getNamespace() + "/" + newStatefulSet.getMetadata().getName());
				}
				if (shouldReconcile(newStatefulSet)) {
					if (DEBUG_SCALING) {
						System.out.println("  -> Enqueueing for reconciliation");
					}
					queue.enqueue(ReconcileRequest.forStatefulSet(newStatefulSet));
				} else if (DEBUG_SCALING) {
					System.out.println("  -> Skipped (no server-discovery label)");
				}
			}

			@Override
			public void onDelete(StatefulSet statefulSet, boolean deletedFinalStateUnknown) {
				if (DEBUG_SCALING) {
					System.out.println("\n[INFORMER] StatefulSet DELETED: " + statefulSet.getMetadata().getNamespace() + "/" + statefulSet.getMetadata().getName());
				}
				// Optional: cleanup logic if needed
			}
		});

		System.out.println("Informers configured for Deployments and StatefulSets");
	}

	private boolean shouldReconcile(HasMetadata resource) {
		Map<String, String> labels = resource.getMetadata().getLabels();
		if (labels == null) {
			return false;
		}

		// Only reconcile resources with the server discovery label
		String serverDiscovery = labels.get(DeploymentLabel.SERVER_DISCOVERY.getLabel());
		return "true".equals(serverDiscovery);
	}

	public void start() {
		System.out.println("Starting informers...");
		informerFactory.startAllRegisteredInformers();

		// Wait for informers to sync
		System.out.println("Waiting for informers to sync...");
		int maxWaitSeconds = 30;
		int waitedSeconds = 0;
		while (!informerFactory.getExistingSharedIndexInformer(Deployment.class).hasSynced() &&
			   waitedSeconds < maxWaitSeconds) {
			try {
				Thread.sleep(1000);
				waitedSeconds++;
				if (DEBUG_SCALING && waitedSeconds % 5 == 0) {
					System.out.println("  Still waiting for sync... (" + waitedSeconds + "s)");
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		if (waitedSeconds >= maxWaitSeconds) {
			System.err.println("Warning: Informers did not sync within " + maxWaitSeconds + " seconds");
		} else {
			System.out.println("Informers synced successfully in " + waitedSeconds + " seconds");

			if (DEBUG_SCALING) {
				// List all discovered deployments
				DeploymentList deployments = client.apps().deployments().inAnyNamespace().list();
				System.out.println("\n[DEBUG] Found " + deployments.getItems().size() + " total deployments in cluster");

				int autoscaledCount = 0;
				for (Deployment d : deployments.getItems()) {
					Map<String, String> labels = d.getMetadata().getLabels();
					if (labels != null && "true".equals(labels.get(DeploymentLabel.SERVER_DISCOVERY.getLabel()))) {
						autoscaledCount++;
						System.out.println("  - " + d.getMetadata().getNamespace() + "/" + d.getMetadata().getName() + " (has server-discovery label)");
					}
				}

				if (autoscaledCount == 0) {
					System.out.println("\n⚠️  WARNING: No deployments found with 'server-discovery: true' label!");
					System.out.println("   The autoscaler will not do anything until you add this label to a deployment.");
					System.out.println("   Example: kubectl label deployment <name> server-discovery=true");
				} else {
					System.out.println("\n✓ Found " + autoscaledCount + " deployment(s) with server-discovery label");
				}
			}
		}

		// Start reconciliation loop
		startReconciliationLoop();
	}

	private void startReconciliationLoop() {
		running.set(true);
		reconciliationThread = new Thread(() -> {
			System.out.println("Reconciliation loop started!");
			if (DEBUG_SCALING) {
				System.out.println("  Waiting for reconciliation requests...\n");
			}
			while (running.get()) {
				try {
					if (DEBUG_SCALING) {
						System.out.println("[RECONCILIATION LOOP] Waiting for next request from queue...");
					}
					ReconcileRequest request = queue.dequeue();

					if (DEBUG_SCALING) {
						System.out.println("[RECONCILIATION LOOP] Dequeued request: " + request.getName());
					}

					// Process the reconciliation request
					ReconcileResult result = reconciler.reconcile(request);

					// Mark as complete (removes from in-flight set)
					queue.markComplete(request);

					// Requeue if needed
					if (result.shouldRequeue()) {
						if (DEBUG_SCALING) {
							System.out.println("[RECONCILIATION LOOP] Requeuing " + request.getName() + " in " + result.getRequeueAfterMs() + "ms");
						}
						queue.requeue(request, result.getRequeueAfterMs());
					} else if (DEBUG_SCALING) {
						System.out.println("[RECONCILIATION LOOP] Not requeuing " + request.getName());
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
