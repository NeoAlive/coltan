package com.neoalive.coltan.client.compat.sbw.light;

import com.neoalive.coltan.ColtanConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Camera distance test for the optional SBW spark distance limit. Client thread only. */
public final class SbwLightGuard {
    private SbwLightGuard() {}

    /** True when a spark at this block is beyond {@code lightDistance} from the camera. */
    public static boolean tooFar(int x, int y, int z) {
        int limit = ColtanConfig.sbwLightDistance;
        if (limit <= 0) {
            return false;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return false;
        }
        Vec3 from = camera.getPosition();
        double dx = x + 0.5D - from.x;
        double dy = y + 0.5D - from.y;
        double dz = z + 0.5D - from.z;
        return dx * dx + dy * dy + dz * dz > (double) limit * limit;
    }
}
