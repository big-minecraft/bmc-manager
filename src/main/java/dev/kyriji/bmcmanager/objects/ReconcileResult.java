package dev.kyriji.bmcmanager.objects;

public class ReconcileResult {
	private final boolean shouldRequeue;
	private final long requeueAfterMs;

	private ReconcileResult(boolean shouldRequeue, long requeueAfterMs) {
		this.shouldRequeue = shouldRequeue;
		this.requeueAfterMs = requeueAfterMs;
	}

	public static ReconcileResult noRequeue() {
		return new ReconcileResult(false, 0);
	}

	public static ReconcileResult requeueAfter(long milliseconds) {
		return new ReconcileResult(true, milliseconds);
	}

	public static ReconcileResult requeue() {
		return requeueAfter(5000); // Default 5 seconds
	}

	public boolean shouldRequeue() {
		return shouldRequeue;
	}

	public long getRequeueAfterMs() {
		return requeueAfterMs;
	}

	@Override
	public String toString() {
		return "ReconcileResult{" +
			   "shouldRequeue=" + shouldRequeue +
			   ", requeueAfterMs=" + requeueAfterMs +
			   '}';
	}
}
