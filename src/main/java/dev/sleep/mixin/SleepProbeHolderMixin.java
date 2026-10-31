package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.SleeperHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Per block entity: the sleep engine's state, plus the two hot fields of the fast skip. Sleep engine only. */
@Mixin(BlockEntity.class)
public abstract class SleepProbeHolderMixin implements SleeperHolder {
    @Unique private Object sleep$sleeper;
    @Unique private long sleep$wakeAt = Long.MIN_VALUE;
    @Unique private long sleep$skipped;
    @Unique private int sleep$age;

    @Override public Object sleep$sleeper() { return sleep$sleeper; }
    @Override public void sleep$setSleeper(Object sleeper) { sleep$sleeper = sleeper; }
    @Override public long sleep$wakeAt() { return sleep$wakeAt; }
    @Override public void sleep$setWakeAt(long wakeAt) { sleep$wakeAt = wakeAt; }
    @Override public long sleep$skipped() { return sleep$skipped; }
    @Override public void sleep$setSkipped(long skipped) { sleep$skipped = skipped; }
    @Override public int sleep$age() { return sleep$age; }
    @Override public void sleep$setAge(int age) { sleep$age = age; }
}
