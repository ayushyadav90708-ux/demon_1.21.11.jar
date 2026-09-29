package com.heaven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Finds blocks of the requested type that are genuinely visible from the player's eyes
 * (an unobstructed line to one of the block's faces), walks to them and mines them.
 * There is no X-ray: hidden blocks are never selected.
 */
public class MiningManager {
    private record Hit(Vec3d pos, Direction side) {}

    private static final int SCAN_RADIUS = 20;
    private static final int IDLE_TIMEOUT_TICKS = 600;
    private static final int PICKUP_TICKS = 80;

    private final HeavenClient heaven;
    private Block target;
    private BlockPos current;
    private int tickCounter;
    private int scanCooldown;
    private int idleTicks;
    private int mined;
    private int pickupTicks;
    private Vec3d pickupPos;
    private final Map<BlockPos, Integer> blacklist = new HashMap<>();

    public MiningManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    public void start(Block block) {
        target = block;
        current = null;
        tickCounter = 0;
        scanCooldown = 0;
        idleTicks = 0;
        mined = 0;
        pickupTicks = 0;
        pickupPos = null;
        blacklist.clear();
    }

    public void stop() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.interactionManager != null) {
            c.interactionManager.cancelBlockBreaking();
        }
        target = null;
        current = null;
        pickupTicks = 0;
    }

    public String targetName() {
        return target == null ? "-" : Registries.BLOCK.getId(target).toString();
    }

    public String stateText() {
        if (target == null) {
            return "Idle";
        }
        if (pickupTicks > 0) {
            return "Collecting drops";
        }
        return current == null ? "Searching" : "Mining " + current.getX() + " " + current.getY() + " " + current.getZ();
    }

    public void tick(MinecraftClient client, InputController in, TaskManager tasks) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        NavigationManager nav = heaven.navigation();
        if (p == null || w == null || client.interactionManager == null || target == null) {
            return;
        }
        tickCounter++;

        if (pickupTicks > 0) {
            collectDrops(client, in, p, w, nav);
            return;
        }

        if (current != null && !w.getBlockState(current).isOf(target)) {
            mined++;
            startPickup(w, current);
            current = null;
            nav.clear();
            if (pickupTicks > 0) {
                return;
            }
        }

        if (current == null) {
            if (--scanCooldown <= 0) {
                scanCooldown = 10;
                current = findNext(w, p);
                nav.clear();
            }
        }

        if (current == null) {
            idleTicks += 1;
            if (idleTicks > IDLE_TIMEOUT_TICKS) {
                Msg.info("No more visible " + targetName() + " nearby. Mined " + mined
                        + ". Move to a new area and run /mine again.");
                tasks.stop(null);
            }
            return;
        }
        idleTicks = 0;

        Hit hit = visibleHit(w, p, current);
        double reach = p.getBlockInteractionRange() - 0.4;
        if (hit != null && p.getEyePos().distanceTo(hit.pos()) <= reach) {
            nav.clear();
            mineNow(client, p, w, hit);
            return;
        }

        client.interactionManager.cancelBlockBreaking();
        if (!nav.hasGoal()) {
            nav.setGoal(Goal.reach(Vec3d.ofCenter(current), Math.max(2.0, reach - 0.6)));
        }
        nav.tick(client, in);
        if (nav.isFailed() || nav.isArrived()) {
            blacklist.put(current, tickCounter + 1200);
            current = null;
            nav.clear();
        }
    }

    private void mineNow(MinecraftClient client, ClientPlayerEntity p, ClientWorld w, Hit hit) {
        BlockState state = w.getBlockState(current);
        selectBestTool(p, state);
        double error = RotationUtil.lookAt(p, hit.pos(), 30.0f);
        if (error < 8.0) {
            client.interactionManager.updateBlockBreakingProgress(current, hit.side());
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    private void selectBestTool(ClientPlayerEntity p, BlockState state) {
        int bestSlot = p.getInventory().getSelectedSlot();
        float bestSpeed = p.getInventory().getStack(bestSlot).getMiningSpeedMultiplier(state);
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            float speed = s.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed + 0.01f) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot != p.getInventory().getSelectedSlot()) {
            p.getInventory().setSelectedSlot(bestSlot);
        }
    }

    private BlockPos findNext(ClientWorld w, ClientPlayerEntity p) {
        BlockPos origin = p.getBlockPos();
        List<BlockPos> matches = new ArrayList<>();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    if (!w.getBlockState(pos).isOf(target)) {
                        continue;
                    }
                    Integer until = blacklist.get(pos);
                    if (until != null && until > tickCounter) {
                        continue;
                    }
                    matches.add(pos);
                }
            }
        }
        Vec3d eye = p.getEyePos();
        matches.sort((a, b) -> Double.compare(Vec3d.ofCenter(a).squaredDistanceTo(eye),
                Vec3d.ofCenter(b).squaredDistanceTo(eye)));
        for (BlockPos pos : matches) {
            if (visibleHit(w, p, pos) != null) {
                return pos;
            }
        }
        return null;
    }

    /** A block counts as visible only if a ray from the eyes reaches one of its faces unobstructed. */
    private Hit visibleHit(ClientWorld w, ClientPlayerEntity p, BlockPos pos) {
        Vec3d eye = p.getEyePos();
        Vec3d c = Vec3d.ofCenter(pos);
        double o = 0.49;
        Vec3d[] samples = {
                c,
                c.add(o, 0, 0), c.add(-o, 0, 0),
                c.add(0, o, 0), c.add(0, -o, 0),
                c.add(0, 0, o), c.add(0, 0, -o)
        };
        for (Vec3d sample : samples) {
            BlockHitResult r = w.raycast(new RaycastContext(eye, sample,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(pos)) {
                return new Hit(r.getPos(), r.getSide());
            }
        }
        return null;
    }

    private void startPickup(ClientWorld w, BlockPos broken) {
        Vec3d center = Vec3d.ofCenter(broken);
        ItemEntity nearest = nearestItem(w, center, 6.0);
        if (nearest != null) {
            pickupTicks = PICKUP_TICKS;
            pickupPos = null;
        }
    }

    private ItemEntity nearestItem(ClientWorld w, Vec3d around, double range) {
        List<ItemEntity> items = w.getEntitiesByClass(ItemEntity.class,
                net.minecraft.util.math.Box.of(around, range * 2, range * 2, range * 2), e -> e.isAlive());
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : items) {
            double d = e.squaredDistanceTo(around);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    private void collectDrops(MinecraftClient client, InputController in, ClientPlayerEntity p,
                              ClientWorld w, NavigationManager nav) {
        pickupTicks--;
        Vec3d me = new Vec3d(p.getX(), p.getY(), p.getZ());
        ItemEntity item = nearestItem(w, me, 8.0);
        if (item == null || pickupTicks <= 0) {
            pickupTicks = 0;
            nav.clear();
            return;
        }
        Vec3d ip = new Vec3d(item.getX(), item.getY(), item.getZ());
        if (pickupPos == null || pickupPos.squaredDistanceTo(ip) > 2.25 || !nav.hasGoal()) {
            pickupPos = ip;
            nav.setGoal(Goal.stand(ip, 0.8));
        }
        nav.tick(client, in);
        if (nav.isArrived() || nav.isFailed()) {
            pickupTicks = 0;
            nav.clear();
        }
    }
}
