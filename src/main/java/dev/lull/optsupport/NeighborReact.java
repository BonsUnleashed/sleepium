package dev.lull.optsupport;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Does a block react to neighbour updates at all? True if its class (or any superclass below BlockBehaviour)
 * overrides neighborChanged; the BlockBehaviour default only sends a debug packet (debug renderer). Decided once per
 * block class by reflection, cached per BlockState (NeighborReactStateMixin). Universal: works for modded blocks.
 */
public final class NeighborReact {
    public static final boolean ON = Boolean.getBoolean("lab.opt.NeighborReactMixin");
    /** 1.20.1: the running name (SRG in Forge production: vanilla and reobfuscated mods both override it under it) */
    private static final String NEIGHBOR_CHANGED = dev.lull.Names.method("net/minecraft/world/level/block/state/BlockBehaviour", "neighborChanged",
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V");
    private static final ClassValue<Boolean> REACTS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != BlockBehaviour.class && c != Object.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod(NEIGHBOR_CHANGED, BlockState.class, Level.class, BlockPos.class, Block.class, BlockPos.class, boolean.class);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
            return false;
        }
    };
    public static long skipped;

    private NeighborReact() {}

    public static boolean reactsSlow(BlockState state) {
        return REACTS.get(state.getBlock().getClass());
    }
}
