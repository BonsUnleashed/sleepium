package dev.lull.mixin;

import dev.lull.optsupport.sleep.EntSleeperHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntSleepHolderMixin implements EntSleeperHolder {
    @Unique private Object sleep$es;

    @Override
    public Object sleep$es() {
        return sleep$es;
    }

    @Override
    public void sleep$setEs(Object s) {
        sleep$es = s;
    }
}
