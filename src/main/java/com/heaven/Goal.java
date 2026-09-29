package com.heaven;

import net.minecraft.util.math.Vec3d;

/** Describes where the navigator should end up. */
public final class Goal {
    private static final double EYE_HEIGHT = 1.62;

    public final Vec3d center;
    public final double radius;
    public final boolean eye;
    public final boolean ignoreY;

    private Goal(Vec3d center, double radius, boolean eye, boolean ignoreY) {
        this.center = center;
        this.radius = radius;
        this.eye = eye;
        this.ignoreY = ignoreY;
    }

    /** Get close enough that the player's eyes are within {@code reach} of the point. */
    public static Goal reach(Vec3d center, double reach) {
        return new Goal(center, reach, true, false);
    }

    /** Stand at (or near) a position, vertical difference matters. */
    public static Goal stand(Vec3d center, double radius) {
        return new Goal(center, radius, false, false);
    }

    /** Reach an X/Z position, ignoring height. */
    public static Goal horizontal(Vec3d center, double radius) {
        return new Goal(center, radius, false, true);
    }

    public boolean reachedBy(Vec3d feet) {
        if (eye) {
            return feet.add(0, EYE_HEIGHT, 0).squaredDistanceTo(center) <= radius * radius;
        }
        double dx = feet.x - center.x;
        double dz = feet.z - center.z;
        if (dx * dx + dz * dz > radius * radius) {
            return false;
        }
        return ignoreY || Math.abs(feet.y - center.y) <= 2.5;
    }

    /** Heuristic remaining distance (never negative). */
    public double distanceEstimate(Vec3d feet) {
        double d;
        if (eye) {
            d = feet.add(0, EYE_HEIGHT, 0).distanceTo(center);
        } else if (ignoreY) {
            double dx = feet.x - center.x;
            double dz = feet.z - center.z;
            d = Math.sqrt(dx * dx + dz * dz);
        } else {
            d = feet.distanceTo(center);
        }
        return Math.max(0.0, d - radius);
    }
}
