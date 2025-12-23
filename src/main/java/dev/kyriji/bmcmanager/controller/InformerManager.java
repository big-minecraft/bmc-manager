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
				if (shouldReconcile(deployment)) {
					queue.enqueue(ReconcileRequest.forDeployment(deployment));
				}
			}

			@Override
			public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
				if (shouldReconcile(newDeployment)) {
					queue.enqueue(ReconcileRequest.forDeployment(newDeployment));
				}
			}

			@Override
			public void onDelete(Deployment deployment, boolean deletedFinalStateUnknown) {
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
				if (shouldReconcile(statefulSet)) {
					queue.enqueue(ReconcileRequest.forStatefulSet(statefulSet));
				}
			}

			@Override
			public void onUpdate(StatefulSet oldStatefulSet, StatefulSet newStatefulSet) {
				if (shouldReconcile(newStatefulSet)) {
					queue.enqueue(ReconcileRequest.forStatefulSet(newStatefulSet));
				}
			}

			@Override
			public void onDelete(StatefulSet statefulSet, boolean deletedFinalStateUnknown) {
				// Optional: cleanup logic if needed
			}
		});

		System.out.println("Informers configured for Deployments and StatefulSets");
	}

	private boolean shouldReconcile(HasMetadata resource) {
		// Check deployment/statefulset metadata labels first
		Map<String, String> labels = resource.getMetadata().getLabels();
		if (labels != null) {
			String serverDiscovery = labels.get(DeploymentLabel.SERVER_DISCOVERY.getLabel());
			if ("true".equals(serverDiscovery)) {
				return true;
			}
		}

		// Also check spec.template.metadata.labels (pod template labels)
		Map<String, String> templateLabels = null;
		if (resource instanceof Deployment deployment) {
			templateLabels = deployment.getSpec().getTemplate().getMetadata().getLabels();
		} else if (resource instanceof StatefulSet statefulSet) {
			templateLabels = statefulSet.getSpec().getTemplate().getMetadata().getLabels();
		}

		if (templateLabels != null) {
			String serverDiscovery = templateLabels.get(DeploymentLabel.SERVER_DISCOVERY.getLabel());
			return "true".equals(serverDiscovery);
		}

		return false;
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
