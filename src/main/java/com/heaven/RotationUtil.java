package com.heaven;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Smooth camera turning. */
public final class RotationUtil {
    private RotationUtil() {}

    public static float yawTo(Vec3d from, Vec3d to) {
        return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
    }

    /** Turns toward a point; returns the remaining angular error (degrees) before this step. */
    public static double lookAt(ClientPlayerEntity p, Vec3d target, float maxStep) {
        Vec3d eye = p.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return setRotation(p, yaw, pitch, maxStep);
    }

    public static double setRotation(ClientPlayerEntity p, float yaw, float pitch, float maxStep) {
        float dYaw = MathHelper.wrapDegrees(yaw - p.getYaw());
        float dPitch = pitch - p.getPitch();
        p.setYaw(p.getYaw() + MathHelper.clamp(dYaw, -maxStep, maxStep));
        p.setPitch(MathHelper.clamp(p.getPitch() + MathHelper.clamp(dPitch, -maxStep, maxStep), -90.0f, 90.0f));
        return Math.max(Math.abs(dYaw), Math.abs(dPitch));
    }
}
