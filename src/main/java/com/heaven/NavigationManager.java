package com.heaven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Local A* path finder + walker. It only plans through chunks the client has loaded,
 * so long trips are done in hops toward the goal as new chunks arrive.
 */
public class NavigationManager {
    public enum State { IDLE, MOVING, ARRIVED, FAILED }

    private static final int MAX_EXPANSIONS = 4000;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final class Node {
        final BlockPos pos;
        final double g;
        final double f;
        final Node parent;

        Node(BlockPos pos, double g, double f, Node parent) {
            this.pos = pos;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }
    }

    private record Result(List<BlockPos> path, boolean reached) {}

    private Goal goal;
    private State state = State.IDLE;
    private List<BlockPos> path = List.of();
    private int index;
    private int ticksSincePlan = 999;
    private int planDelay;
    private int failedPlans;
    private int stuckChecks;
    private int checkTimer;
    private Vec3d lastCheckPos;

    public void setGoal(Goal newGoal) {
        goal = newGoal;
        state = State.MOVING;
        path = List.of();
        index = 0;
        ticksSincePlan = 999;
        planDelay = 0;
        failedPlans = 0;
        stuckChecks = 0;
        checkTimer = 0;
        lastCheckPos = null;
    }

    public void clear() {
        goal = null;
        state = State.IDLE;
        path = List.of();
        index = 0;
    }

    /** Forces a fresh plan on the next tick (e.g. after leaving a boat). */
    public void invalidate() {
        ticksSincePlan = 999;
        planDelay = 0;
    }

    public boolean hasGoal() { return goal != null; }
    public Goal goal() { return goal; }
    public boolean isArrived() { return state == State.ARRIVED; }
    public boolean isFailed() { return state == State.FAILED; }
    public State state() { return state; }

    private static Vec3d feet(ClientPlayerEntity p) {
        return new Vec3d(p.getX(), p.getY(), p.getZ());
    }

    private static Vec3d nodeCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public void tick(MinecraftClient client, InputController in) {
        if (goal == null || state != State.MOVING) {
            return;
        }
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null) {
            return;
        }
        Vec3d feet = feet(p);
        if (goal.reachedBy(feet)) {
            state = State.ARRIVED;
            return;
        }

        ticksSincePlan++;
        if (++checkTimer >= 30) {
            checkTimer = 0;
            if (lastCheckPos != null) {
                double mx = feet.x - lastCheckPos.x;
                double mz = feet.z - lastCheckPos.z;
                if (mx * mx + mz * mz < 0.25 && Math.abs(feet.y - lastCheckPos.y) < 0.5) {
                    stuckChecks++;
                    ticksSincePlan = 999;
                    if (stuckChecks >= 6) {
                        state = State.FAILED;
                        return;
                    }
                } else {
                    stuckChecks = 0;
                }
            }
            lastCheckPos = feet;
        }

        if (planDelay > 0) {
            planDelay--;
            return;
        }
        if (index >= path.size() || ticksSincePlan > 100) {
            plan(w, p);
            if (state == State.FAILED || path.isEmpty()) {
                return;
            }
        }

        // Advance along the path.
        while (index < path.size()) {
            Vec3d wc = nodeCenter(path.get(index));
            double dx = wc.x - feet.x;
            double dz = wc.z - feet.z;
            if (Math.sqrt(dx * dx + dz * dz) < 0.45 && Math.abs(feet.y - wc.y) < 1.2) {
                index++;
            } else {
                break;
            }
        }
        if (index >= path.size()) {
            ticksSincePlan = 999;
            return;
        }

        BlockPos wp = path.get(index);
        Vec3d wc = nodeCenter(wp);
        float desiredYaw = RotationUtil.yawTo(feet, wc);
        RotationUtil.setRotation(p, desiredYaw, 0.0f, 25.0f);
        float yawError = Math.abs(MathHelper.wrapDegrees(desiredYaw - p.getYaw()));

        boolean aligned = yawError < 50.0f;
        in.forward(aligned);
        boolean hungerOk = p.getHungerManager().getFoodLevel() > 6;
        in.sprint(aligned && yawError < 25.0f && hungerOk && !p.isTouchingWater());

        boolean wantsUp = wp.getY() > p.getBlockY();
        if ((wantsUp && p.isOnGround()) || (p.horizontalCollision && p.isOnGround()) || p.isTouchingWater()) {
            in.jump(true);
        }
    }

    private void plan(ClientWorld w, ClientPlayerEntity p) {
        ticksSincePlan = 0;
        Vec3d feet = feet(p);
        double before = goal.distanceEstimate(feet);
        Result r = search(w, p.getBlockPos());
        path = r.path();
        index = 0;
        if (path.isEmpty()) {
            failedPlans++;
            planDelay = 20;
        } else if (!r.reached()
                && goal.distanceEstimate(nodeCenter(path.get(path.size() - 1))) > before - 1.0) {
            failedPlans++;
        } else {
            failedPlans = 0;
        }
        if (failedPlans >= 4) {
            state = State.FAILED;
        }
    }

    private double heuristic(BlockPos pos) {
        return goal.distanceEstimate(nodeCenter(pos)) * 1.25;
    }

    private Result search(ClientWorld w, BlockPos start) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble((Node n) -> n.f));
        Map<Long, Double> bestG = new HashMap<>();
        Set<Long> closed = new HashSet<>();

        Node startNode = new Node(start, 0.0, heuristic(start), null);
        open.add(startNode);
        bestG.put(start.asLong(), 0.0);

        Node best = startNode;
        double bestH = goal.distanceEstimate(nodeCenter(start));
        int expanded = 0;

        while (!open.isEmpty() && expanded < MAX_EXPANSIONS) {
            Node cur = open.poll();
            long key = cur.pos.asLong();
            if (!closed.add(key)) {
                continue;
            }
            expanded++;

            if (goal.reachedBy(nodeCenter(cur.pos))) {
                return new Result(build(cur), true);
            }
            double h = goal.distanceEstimate(nodeCenter(cur.pos));
            if (h < bestH) {
                bestH = h;
                best = cur;
            }

            for (int[] d : DIRS) {
                BlockPos next = null;
                double cost = 1.0;
                int nx = cur.pos.getX() + d[0];
                int nz = cur.pos.getZ() + d[1];
                BlockPos same = new BlockPos(nx, cur.pos.getY(), nz);

                if (WorldUtil.isWalkable(w, same)) {
                    next = same;
                } else if (WorldUtil.isPassable(w, same) && WorldUtil.isPassable(w, same.up())) {
                    // Nothing to stand on: look for a safe drop of up to 3 blocks.
                    for (int drop = 1; drop <= 3; drop++) {
                        BlockPos below = same.down(drop);
                        if (WorldUtil.isWalkable(w, below)) {
                            next = below;
                            cost = 1.0 + 0.5 * drop;
                            break;
                        }
                        if (!WorldUtil.isPassable(w, below)) {
                            break;
                        }
                    }
                } else {
                    // Blocked ahead: try a one block step up (needs headroom above us).
                    BlockPos up = same.up();
                    if (WorldUtil.isWalkable(w, up) && WorldUtil.isPassable(w, cur.pos.up(2))) {
                        next = up;
                        cost = 1.6;
                    }
                }

                if (next == null) {
                    continue;
                }
                if (WorldUtil.isWater(w, next)) {
                    cost += 1.0;
                }
                long nk = next.asLong();
                if (closed.contains(nk)) {
                    continue;
                }
                double ng = cur.g + cost;
                Double old = bestG.get(nk);
                if (old != null && old <= ng) {
                    continue;
                }
                bestG.put(nk, ng);
                open.add(new Node(next, ng, ng + heuristic(next), cur));
            }
        }
        return new Result(build(best), false);
    }

    private static List<BlockPos> build(Node end) {
        List<BlockPos> out = new ArrayList<>();
        Node n = end;
        while (n != null && n.parent != null) {
            out.add(n.pos);
            n = n.parent;
        }
        Collections.reverse(out);
        return out;
    }
}
