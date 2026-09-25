package dev.probe.mixin;

import dev.probe.optsupport.sleep.SleeperHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** One extra field per block entity: the sleep engine's state, reached without any map lookup. Sleep engine only. */
@Mixin(BlockEntity.class)
public abstract class SleepProbeHolderMixin implements SleeperHolder {
    @Unique
    private Object probe$sleeper;

    @Override
    public Object probe$sleeper() {
        return probe$sleeper;
    }

    @Override
    public void probe$setSleeper(Object sleeper) {
        probe$sleeper = sleeper;
    }
}
