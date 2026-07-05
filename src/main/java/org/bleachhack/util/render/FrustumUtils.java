package org.bleachhack.util.render;

import org.bleachhack.mixin.AccessorFrustum;

import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class FrustumUtils {

	// 1.21.11's WorldRenderer no longer keeps a persistent "current frustum" field (frustum culling
	// was reworked to pass the Frustum through the per-frame render call chain as a parameter
	// instead) - so there's nothing left for an Accessor mixin to expose. MixinWorldRenderer instead
	// captures the live per-frame Frustum straight out of fillEntityRenderStates(...) into this field.
	private static Frustum currentFrustum;

	public static void setCurrentFrustum(Frustum frustum) {
		currentFrustum = frustum;
	}

	public static Frustum getFrustum() {
		return currentFrustum;
	}

	public static boolean isBoxVisible(Box box) {
		return getFrustum().isVisible(box);
	}

	public static boolean isPointVisible(Vec3d vec) {
		return isPointVisible(vec.x, vec.y, vec.z);
	}

	public static boolean isPointVisible(double x, double y, double z) {
		AccessorFrustum frustum = (AccessorFrustum) getFrustum();
		return frustum.getFrustumIntersection().testPoint((float) (x - frustum.getX()), (float) (y - frustum.getY()), (float) (z - frustum.getZ()));
	}
}
