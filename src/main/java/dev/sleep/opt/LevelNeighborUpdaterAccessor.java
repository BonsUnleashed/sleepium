package dev.sleep.opt;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Level.neighborUpdater for NeighborNotifySkipMixin. */
@Mixin(Level.class)
public interface LevelNeighborUpdaterAccessor {
    @Accessor("neighborUpdater")
    NeighborUpdater probe$neighborUpdater();
}
