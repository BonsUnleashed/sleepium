package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.SleeperHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Per block entity: the sleep engine's state, plus the two hot fields of the fast skip. Sleep engine only. */
@Mixin(BlockEntity.class)
public abstract class SleepProbeHolderMixin implements SleeperHolder {
    @Unique private Object probe$sleeper;
    @Unique private long probe$wakeAt = Long.MIN_VALUE;
    @Unique private long probe$skipped;
    @Unique private int probe$age;

    @Override public Object probe$sleeper() { return probe$sleeper; }
    @Override public void probe$setSleeper(Object sleeper) { probe$sleeper = sleeper; }
    @Override public long probe$wakeAt() { return probe$wakeAt; }
    @Override public void probe$setWakeAt(long wakeAt) { probe$wakeAt = wakeAt; }
    @Override public long probe$skipped() { return probe$skipped; }
    @Override public void probe$setSkipped(long skipped) { probe$skipped = skipped; }
    @Override public int probe$age() { return probe$age; }
    @Override public void probe$setAge(int age) { probe$age = age; }
}
