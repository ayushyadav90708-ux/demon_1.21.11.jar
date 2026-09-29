package com.heaven;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

/**
 * Defends against nearby hostile mobs only. Players are never targeted:
 * the target filter accepts nothing that is not a Monster.
 */
public class CombatManager {
    private static final double DETECT_RANGE = 6.0;
    private static final double LOSE_RANGE = 10.0;
    private static final double ATTACK_RANGE = 2.9;

    private final HeavenClient heaven;
    private LivingEntity target;

    public CombatManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    private static boolean isThreat(LivingEntity e, ClientPlayerEntity self) {
        if (e == self || !e.isAlive() || !(e instanceof Monster)) {
            return false;
        }
        // Neutral-by-default or extremely dangerous mobs are left alone.
        return !(e instanceof EndermanEntity) && !(e instanceof ZombifiedPiglinEntity)
                && !(e instanceof WardenEntity);
    }

    private static boolean isWeapon(ItemStack s) {
        return !s.isEmpty() && (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES));
    }

    /** @return true while fighting (movement is taken over by combat). */
    public boolean tick(MinecraftClient client, InputController in) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null || client.interactionManager == null || !heaven.config().autoDefense
                || p.isGliding()) {
            target = null;
            return false;
        }

        if (target != null && (!target.isAlive() || p.distanceTo(target) > LOSE_RANGE)) {
            target = null;
        }
        if (target == null) {
            Box box = p.getBoundingBox().expand(DETECT_RANGE);
            List<LivingEntity> found = w.getEntitiesByClass(LivingEntity.class, box,
                    e -> isThreat(e, p) && p.canSee(e));
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity e : found) {
                double d = p.squaredDistanceTo(e);
                if (d < bestDist) {
                    bestDist = d;
                    target = e;
                }
            }
        }
        if (target == null) {
            return false;
        }

        client.interactionManager.cancelBlockBreaking();

        // Use a weapon if one is on the hotbar, otherwise whatever is in hand.
        if (!isWeapon(p.getMainHandStack())) {
            int slot = InventoryUtil.findHotbar(p, CombatManager::isWeapon);
            if (slot >= 0) {
                p.getInventory().setSelectedSlot(slot);
            }
        }

        RotationUtil.lookAt(p, target.getBoundingBox().getCenter(), 35.0f);
        double dist = p.distanceTo(target);
        if (dist > 2.6) {
            in.forward(true);
            if (p.horizontalCollision && p.isOnGround()) {
                in.jump(true);
            }
        }
        if (dist <= ATTACK_RANGE && p.getAttackCooldownProgress(0.5f) >= 0.95f) {
            client.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }
        return true;
    }

    public String status() {
        if (!heaven.config().autoDefense) {
            return "Off";
        }
        return target != null ? "Fighting " + target.getType().getName().getString() : "Clear";
    }

    public void reset() {
        target = null;
    }
}
