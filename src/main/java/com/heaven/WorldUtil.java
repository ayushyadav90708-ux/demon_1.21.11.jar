package com.heaven;

import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

/** World queries used by navigation. Unloaded chunks are never treated as walkable. */
public final class WorldUtil {
    private WorldUtil() {}

    private static final Set<Block> HAZARDS = Set.of(
            Blocks.CACTUS, Blocks.MAGMA_BLOCK, Blocks.FIRE, Blocks.SOUL_FIRE,
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.SWEET_BERRY_BUSH,
            Blocks.WITHER_ROSE, Blocks.POWDER_SNOW, Blocks.COBWEB, Blocks.LAVA);

    public static boolean isLoaded(ClientWorld w, BlockPos pos) {
        if (w.isOutOfHeightLimit(pos)) {
            return false;
        }
        return w.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) != null;
    }

    public static boolean isWater(ClientWorld w, BlockPos pos) {
        return w.getFluidState(pos).isIn(FluidTags.WATER);
    }

    public static boolean isPassable(ClientWorld w, BlockPos pos) {
        if (!isLoaded(w, pos)) {
            return false;
        }
        BlockState state = w.getBlockState(pos);
        if (HAZARDS.contains(state.getBlock())) {
            return false;
        }
        if (w.getFluidState(pos).isIn(FluidTags.LAVA)) {
            return false;
        }
        return state.getCollisionShape(w, pos).isEmpty();
    }

    public static boolean isSolidFloor(ClientWorld w, BlockPos pos) {
        if (!isLoaded(w, pos)) {
            return false;
        }
        BlockState state = w.getBlockState(pos);
        if (HAZARDS.contains(state.getBlock())) {
            return false;
        }
        return !state.getCollisionShape(w, pos).isEmpty();
    }

    /** Feet position that a player can stand in (or swim through). */
    public static boolean isWalkable(ClientWorld w, BlockPos pos) {
        return isPassable(w, pos) && isPassable(w, pos.up())
                && (isSolidFloor(w, pos.down()) || isWater(w, pos));
    }
}
