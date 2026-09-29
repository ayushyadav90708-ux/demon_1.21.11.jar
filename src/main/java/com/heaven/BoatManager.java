package com.heaven;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Places, boards, steers and leaves a boat when the trip crosses open water. */
public class BoatManager {
    private enum State { IDLE, PLACING, BOARDING, SAILING, EXITING }

    private final HeavenClient heaven;
    private State state = State.IDLE;
    private int timer;
    private int cooldown;
    private AbstractBoatEntity ridden;
    private int recoverAttacks;

    public BoatManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    private static boolean isBoat(ItemStack s) {
        return !s.isEmpty() && s.isIn(ItemTags.BOATS);
    }

    /** @return true while the boat logic owns movement this tick. */
    public boolean tick(MinecraftClient client, InputController in, Vec3d dest) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null || client.interactionManager == null || !heaven.config().boatAssist) {
            return false;
        }
        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        double dx = dest.x - pos.x;
        double dz = dest.z - pos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        switch (state) {
            case IDLE -> {
                if (cooldown > 0) {
                    cooldown--;
                    return false;
                }
                if (p.hasVehicle() || p.isGliding() || dist < 24.0 || !InventoryUtil.has(p, BoatManager::isBoat)
                        || !waterAhead(w, p, dx / Math.max(dist, 0.001), dz / Math.max(dist, 0.001))) {
                    return false;
                }
                state = State.PLACING;
                timer = 0;
                return true;
            }
            case PLACING -> {
                timer++;
                if (timer > 80 || p.hasVehicle()) {
                    return abort(client, in);
                }
                AbstractBoatEntity nearby = nearestBoat(w, p, 5.0);
                if (nearby != null) {
                    ridden = nearby;
                    state = State.BOARDING;
                    timer = 0;
                    return true;
                }
                InventoryUtil.Prep prep = InventoryUtil.prepare(client, BoatManager::isBoat, true);
                if (prep == InventoryUtil.Prep.MISSING) {
                    return abort(client, in);
                }
                if (prep == InventoryUtil.Prep.PENDING) {
                    return true;
                }
                double nx = dx / Math.max(dist, 0.001);
                double nz = dz / Math.max(dist, 0.001);
                Vec3d spot = new Vec3d(pos.x + nx * 3.0, pos.y - 0.6, pos.z + nz * 3.0);
                double error = RotationUtil.lookAt(p, spot, 30.0f);
                if (error < 6.0 && timer % 8 == 0) {
                    client.interactionManager.interactItem(p, Hand.MAIN_HAND);
                }
                return true;
            }
            case BOARDING -> {
                timer++;
                if (p.hasVehicle()) {
                    state = State.SAILING;
                    timer = 0;
                    return true;
                }
                if (timer > 80 || ridden == null || !ridden.isAlive()) {
                    return abort(client, in);
                }
                RotationUtil.lookAt(p, ridden.getBoundingBox().getCenter(), 40.0f);
                if (timer % 5 == 0 && p.distanceTo(ridden) < 4.0f) {
                    client.interactionManager.interactEntity(p, ridden, Hand.MAIN_HAND);
                }
                return true;
            }
            case SAILING -> {
                timer++;
                if (!(p.getVehicle() instanceof AbstractBoatEntity boat)) {
                    state = State.IDLE;
                    cooldown = 100;
                    heaven.navigation().invalidate();
                    return false;
                }
                ridden = boat;
                Vec3d bp = new Vec3d(boat.getX(), boat.getY(), boat.getZ());
                float desired = RotationUtil.yawTo(bp, dest);
                float diff = MathHelper.wrapDegrees(desired - boat.getYaw());
                in.forward(Math.abs(diff) < 100.0f);
                if (diff > 8.0f) {
                    in.right(true);
                } else if (diff < -8.0f) {
                    in.left(true);
                }
                RotationUtil.setRotation(p, desired, 5.0f, 10.0f);

                double bdx = dest.x - bp.x;
                double bdz = dest.z - bp.z;
                boolean near = Math.sqrt(bdx * bdx + bdz * bdz) < 6.0;
                if (timer > 40 && (near || landAhead(w, boat))) {
                    state = State.EXITING;
                    timer = 0;
                    recoverAttacks = 0;
                }
                return true;
            }
            case EXITING -> {
                timer++;
                if (p.hasVehicle()) {
                    in.sneak(true);
                    if (timer > 60) {
                        return abort(client, in);
                    }
                    return true;
                }
                // Pick the boat back up (break it) so it can be reused.
                if (ridden != null && ridden.isAlive() && recoverAttacks < 10 && timer < 100
                        && p.distanceTo(ridden) < 3.0f) {
                    RotationUtil.lookAt(p, ridden.getBoundingBox().getCenter(), 40.0f);
                    if (p.getAttackCooldownProgress(0.5f) >= 0.9f) {
                        client.interactionManager.attackEntity(p, ridden);
                        p.swingHand(Hand.MAIN_HAND);
                        recoverAttacks++;
                    }
                    return true;
                }
                state = State.IDLE;
                cooldown = 200;
                ridden = null;
                heaven.navigation().invalidate();
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean abort(MinecraftClient client, InputController in) {
        state = State.IDLE;
        cooldown = 400;
        ridden = null;
        heaven.navigation().invalidate();
        return false;
    }

    private static AbstractBoatEntity nearestBoat(ClientWorld w, ClientPlayerEntity p, double range) {
        Box box = p.getBoundingBox().expand(range);
        List<AbstractBoatEntity> boats = w.getEntitiesByClass(AbstractBoatEntity.class, box, e -> e.isAlive());
        AbstractBoatEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (AbstractBoatEntity b : boats) {
            double d = p.squaredDistanceTo(b);
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        return best;
    }

    /** True when most of the next few blocks toward the destination are water surface. */
    private static boolean waterAhead(ClientWorld w, ClientPlayerEntity p, double nx, double nz) {
        int water = 0;
        int samples = 0;
        for (int d = 2; d <= 9; d++) {
            samples++;
            BlockPos base = BlockPos.ofFloored(p.getX() + nx * d, p.getY(), p.getZ() + nz * d);
            if (WorldUtil.isWater(w, base.down()) || WorldUtil.isWater(w, base)) {
                water++;
            }
        }
        return water >= samples - 1;
    }

    private static boolean landAhead(ClientWorld w, AbstractBoatEntity boat) {
        double yawRad = Math.toRadians(boat.getYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        BlockPos ahead = BlockPos.ofFloored(boat.getX() + fx * 3.5, boat.getY(), boat.getZ() + fz * 3.5);
        return !WorldUtil.isWater(w, ahead) && !WorldUtil.isWater(w, ahead.down());
    }

    public String stateText() {
        return switch (state) {
            case IDLE -> "Idle";
            case PLACING -> "Placing boat";
            case BOARDING -> "Boarding";
            case SAILING -> "Sailing";
            case EXITING -> "Leaving boat";
        };
    }

    public void reset() {
        state = State.IDLE;
        timer = 0;
        cooldown = 0;
        ridden = null;
    }
}
