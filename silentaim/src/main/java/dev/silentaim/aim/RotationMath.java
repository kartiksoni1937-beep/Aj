package dev.silentaim.aim;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pure rotation helpers: no state, no Minecraft side effects.
 */
public final class RotationMath {

    private RotationMath() {
    }

    /**
     * Calculates the yaw/pitch pair that looks from {@code from} towards {@code to}.
     */
    public static float[] lookAt(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    /**
     * Moves {@code from} towards {@code to} by at most {@code maxStep} degrees, taking the
     * shortest way around the 360 degree wrap.
     */
    public static float step(float from, float to, float maxStep) {
        float delta = MathHelper.wrapDegrees(to - from);
        if (maxStep <= 0.0F || Math.abs(delta) <= maxStep) {
            return MathHelper.wrapDegrees(from + delta);
        }

        return MathHelper.wrapDegrees(from + Math.signum(delta) * maxStep);
    }

    /**
     * Absolute angular distance between two angles, in degrees.
     */
    public static float offset(float target, float current) {
        return Math.abs(MathHelper.wrapDegrees(target - current));
    }
}
