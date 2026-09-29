package com.heaven;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Long-distance flight help for players who already wear an Elytra and carry fireworks.
 * Rockets are only used when speed drops and the destination is still far away.
 */
public class ElytraManager {
    private enum State { IDLE, TAKEOFF, FLYING }

    private static final double MIN_DISTANCE = 180.0;
    private static final double LAND_DISTANCE = 40.0;

    private final HeavenClient heaven;
    private State state = State.IDLE;
    private int timer;
    private int flightTicks;
    private int lastBoost;
    private int cooldown;
    private boolean landing;

    public ElytraManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    private static boolean isFirework(ItemStack s) {
        return !s.isEmpty() && s.isOf(Items.FIREWORK_ROCKET);
    }

    private static boolean elytraUsable(ClientPlayerEntity p) {
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        return chest.isOf(Items.ELYTRA) && chest.getDamage() < chest.getMaxDamage() - 1;
    }

    /** @return true while Elytra logic owns movement this tick. */
    public boolean tick(MinecraftClient client, InputController in, Vec3d dest) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null || client.interactionManager == null || !heaven.config().elytraAssist) {
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
                if (!p.isOnGround() || p.hasVehicle() || p.isTouchingWater() || dist < MIN_DISTANCE
                        || !elytraUsable(p) || InventoryUtil.count(p, ElytraManager::isFirework) < 2) {
                    return false;
                }
                state = State.TAKEOFF;
                timer = 0;
                landing = false;
                return true;
            }
            case TAKEOFF -> {
                timer++;
                if (p.isGliding()) {
                    state = State.FLYING;
                    flightTicks = 0;
                    lastBoost = -100;
                    return true;
                }
                if (timer > 80 || !elytraUsable(p)) {
                    state = State.IDLE;
                    cooldown = 600;
                    return false;
                }
                RotationUtil.setRotation(p, RotationUtil.yawTo(pos, dest), -10.0f, 20.0f);
                // Jump, then release/press again in the air to start gliding like a normal player.
                in.jump(p.isOnGround() || timer % 2 == 0);
                return true;
            }
            case FLYING -> {
                flightTicks++;
                if (!p.isGliding()) {
                    if (flightTicks > 5) {
                        state = State.IDLE;
                        cooldown = 200;
                        heaven.navigation().invalidate();
                        return false;
                    }
                    return true;
                }
                int rockets = InventoryUtil.count(p, ElytraManager::isFirework);
                if (dist < LAND_DISTANCE || rockets == 0 || !elytraUsable(p)) {
                    landing = true;
                }

                double speed = p.getVelocity().length();
                double alt = altitudeAboveGround(w, p);
                float pitch;
                if (landing) {
                    pitch = alt > 25 ? 30.0f : (alt > 8 ? 12.0f : 0.0f);
                } else if (alt < 40) {
                    pitch = -20.0f;
                } else {
                    pitch = alt < 80 ? -5.0f : 5.0f;
                }
                if (obstacleAhead(w, p)) {
                    pitch = -35.0f;
                }
                RotationUtil.setRotation(p, RotationUtil.yawTo(pos, dest), pitch, 12.0f);

                if (!landing && speed < 1.2 && dist > 80.0 && flightTicks - lastBoost >= 30) {
                    InventoryUtil.Prep prep = InventoryUtil.prepare(client, ElytraManager::isFirework, true);
                    if (prep == InventoryUtil.Prep.READY) {
                        client.interactionManager.interactItem(p, Hand.MAIN_HAND);
                        lastBoost = flightTicks;
                    }
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static double altitudeAboveGround(ClientWorld w, ClientPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        for (int i = 0; i < 64; i++) {
            BlockPos check = base.down(i);
            if (WorldUtil.isSolidFloor(w, check)) {
                return p.getY() - (check.getY() + 1);
            }
        }
        return 64.0;
    }

    private static boolean obstacleAhead(ClientWorld w, ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        if (v.lengthSquared() < 0.01) {
            return false;
        }
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(v.normalize().multiply(24.0));
        BlockHitResult r = w.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return r.getType() == HitResult.Type.BLOCK;
    }

    public String stateText() {
        return switch (state) {
            case IDLE -> "Idle";
            case TAKEOFF -> "Taking off";
            case FLYING -> landing ? "Landing" : "Flying";
        };
    }

    public void reset() {
        state = State.IDLE;
        timer = 0;
        cooldown = 0;
        landing = false;
    }
}
