package dev.kyriji.bmcmanager.interfaces;

import dev.kyriji.bmcmanager.objects.ScalingSettings;
import dev.wiji.bigminecraftapi.objects.MinecraftInstance;

import java.util.List;

public interface Scalable {

	ScalingSettings getScalingSettings();

	boolean isOnScaleUpCooldown();

	boolean isOnScaleDownCooldown();

	void setLastScaleUp(long lastScaleUp);

	void setLastScaleDown(long lastScaleDown);
}
