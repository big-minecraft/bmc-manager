package dev.kyriji.bmcmanager.objects;

import dev.kyriji.bigminecraftapi.objects.Instance;
import dev.kyriji.bmcmanager.enums.ScaleResult;

import java.util.ArrayList;
import java.util.List;

public class ScalingDecision {
	private final ScaleResult action;
	private final int currentReplicas;
	private final int targetReplicas;
	private final List<? extends Instance> podsToDelete;
	private final long requeueAfterMs;

	public ScalingDecision(ScaleResult action, int currentReplicas, int targetReplicas,
						   List<? extends Instance> podsToDelete, long requeueAfterMs) {
		this.action = action;
		this.currentReplicas = currentReplicas;
		this.targetReplicas = targetReplicas;
		this.podsToDelete = podsToDelete != null ? new ArrayList<>(podsToDelete) : new ArrayList<>();
		this.requeueAfterMs = requeueAfterMs;
	}

	public static ScalingDecision noChange(int currentReplicas) {
		return new ScalingDecision(ScaleResult.NO_CHANGE, currentReplicas, currentReplicas, null, 5000);
	}

	public static ScalingDecision scaleUp(int currentReplicas, int targetReplicas) {
		return new ScalingDecision(ScaleResult.UP, currentReplicas, targetReplicas, null, 5000);
	}

	public static ScalingDecision scaleDown(int currentReplicas, int targetReplicas,
											List<? extends Instance> podsToDelete) {
		return new ScalingDecision(ScaleResult.DOWN, currentReplicas, targetReplicas, podsToDelete, 5000);
	}

	public ScaleResult getAction() {
		return action;
	}

	public int getCurrentReplicas() {
		return currentReplicas;
	}

	public int getTargetReplicas() {
		return targetReplicas;
	}

	public List<? extends Instance> getPodsToDelete() {
		return new ArrayList<>(podsToDelete);
	}

	public long getRequeueAfterMs() {
		return requeueAfterMs;
	}

	@Override
	public String toString() {
		return "ScalingDecision{" +
			   "action=" + action +
			   ", currentReplicas=" + currentReplicas +
			   ", targetReplicas=" + targetReplicas +
			   ", podsToDeleteCount=" + podsToDelete.size() +
			   ", requeueAfterMs=" + requeueAfterMs +
			   '}';
	}
}
