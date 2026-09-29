package com.heaven;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Owns the single active Heaven task and decides which subsystem controls the player each tick. */
public class TaskManager {
    public enum TaskType {
        IDLE("Idle"), MINING("Mining"), TRAVELLING("Travelling");

        public final String label;

        TaskType(String label) {
            this.label = label;
        }
    }

    private final HeavenClient heaven;
    private TaskType type = TaskType.IDLE;
    private Vec3d destination;
    private String targetText = "-";

    public TaskManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    public TaskType type() { return type; }
    public boolean isActive() { return type != TaskType.IDLE; }
    public String targetText() { return targetText; }

    public void startMining(Block block) {
        stop(null);
        heaven.mining().start(block);
        type = TaskType.MINING;
        targetText = heaven.mining().targetName();
        destination = null;
    }

    public void startTravel(int x, int y, int z, boolean ignoreY) {
        stop(null);
        Vec3d center = new Vec3d(x + 0.5, y, z + 0.5);
        destination = center;
        targetText = ignoreY ? x + " ~ " + z : x + " " + y + " " + z;
        heaven.navigation().setGoal(ignoreY ? Goal.horizontal(center, 1.5) : Goal.stand(center, 1.5));
        type = TaskType.TRAVELLING;
    }

    /** Stops everything; a non-null reason is shown to the player if a task was running. */
    public void stop(String reason) {
        boolean was = type != TaskType.IDLE;
        MinecraftClient client = MinecraftClient.getInstance();
        type = TaskType.IDLE;
        destination = null;
        targetText = "-";
        heaven.navigation().clear();
        heaven.mining().stop();
        heaven.boat().reset();
        heaven.elytra().reset();
        heaven.combat().reset();
        heaven.food().reset();
        heaven.input().release(client);
        if (reason != null && was) {
            Msg.info(reason);
        }
    }

    public void tick(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        InputController in = heaven.input();
        if (p == null || w == null) {
            if (type != TaskType.IDLE) {
                stop(null);
            }
            return;
        }
        if (type == TaskType.IDLE || client.currentScreen != null) {
            in.release(client);
            return;
        }

        in.begin();
        boolean fighting = heaven.combat().tick(client, in);
        boolean eating = !fighting && heaven.food().tick(client, in);
        if (fighting || eating) {
            if (client.interactionManager != null) {
                client.interactionManager.cancelBlockBreaking();
            }
        } else {
            switch (type) {
                case MINING -> heaven.mining().tick(client, in, this);
                case TRAVELLING -> travelTick(client, in);
                default -> { }
            }
        }
        if (type != TaskType.IDLE) {
            in.apply(client);
        }
    }

    private void travelTick(MinecraftClient client, InputController in) {
        if (destination == null) {
            return;
        }
        if (heaven.boat().tick(client, in, destination)) {
            return;
        }
        if (heaven.elytra().tick(client, in, destination)) {
            return;
        }
        NavigationManager nav = heaven.navigation();
        nav.tick(client, in);
        if (nav.isArrived()) {
            stop("Arrived at " + targetText + ".");
        } else if (nav.isFailed()) {
            stop("Couldn't find a way to " + targetText
                    + " (blocked, water/lava, or terrain not loaded). Stopped.");
        }
    }

    public String destinationText() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (destination == null || c.player == null) {
            return "-";
        }
        double dx = destination.x - c.player.getX();
        double dz = destination.z - c.player.getZ();
        long dist = Math.round(Math.sqrt(dx * dx + dz * dz));
        return BlockPos.ofFloored(destination).toShortString() + " (" + dist + " blocks)";
    }

    public String detailText() {
        return switch (type) {
            case IDLE -> heaven.find().isWaiting() ? "Finding " + heaven.find().waitingFor() : "Idle";
            case MINING -> heaven.mining().stateText();
            case TRAVELLING -> {
                if (!"Idle".equals(heaven.boat().stateText())) {
                    yield heaven.boat().stateText();
                }
                if (!"Idle".equals(heaven.elytra().stateText())) {
                    yield heaven.elytra().stateText();
                }
                yield heaven.navigation().state().name().charAt(0)
                        + heaven.navigation().state().name().substring(1).toLowerCase();
            }
        };
    }
}
