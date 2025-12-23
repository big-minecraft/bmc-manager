package dev.kyriji.bmcmanager.controller;

import dev.kyriji.bmcmanager.objects.ReconcileRequest;

import java.util.Set;
import java.util.concurrent.*;

public class ReconciliationQueue {
	private final BlockingQueue<ReconcileRequest> queue;
	private final Set<ReconcileRequest> inFlight;
	private final ScheduledExecutorService scheduler;

	public ReconciliationQueue() {
		this.queue = new LinkedBlockingQueue<>();
		this.inFlight = ConcurrentHashMap.newKeySet();
		this.scheduler = Executors.newScheduledThreadPool(1, r -> {
			Thread t = new Thread(r, "reconciliation-requeue-scheduler");
			t.setDaemon(true);
			return t;
		});
	}

	public synchronized void enqueue(ReconcileRequest request) {
		if (inFlight.contains(request)) {
			// Already being processed or queued
			return;
		}

		inFlight.add(request);
		queue.offer(request);
	}

	public ReconcileRequest dequeue() throws InterruptedException {
		return queue.take();
	}

	public void requeue(ReconcileRequest request, long delayMs) {
		scheduler.schedule(() -> {
			synchronized (this) {
				// Remove from in-flight first
				inFlight.remove(request);
				// Create new request with same details (for fresh timestamp)
				ReconcileRequest newRequest = new ReconcileRequest(
					request.getNamespace(),
					request.getName(),
					request.getResourceType()
				);
				enqueue(newRequest);
			}
		}, delayMs, TimeUnit.MILLISECONDS);
	}

	public synchronized void markComplete(ReconcileRequest request) {
		inFlight.remove(request);
	}

	public int size() {
		return queue.size();
	}

	public int inFlightCount() {
		return inFlight.size();
	}

	public void shutdown() {
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
