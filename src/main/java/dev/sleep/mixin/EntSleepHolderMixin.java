package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.EntSleeperHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntSleepHolderMixin implements EntSleeperHolder {
    @Unique private Object probe$es;

    @Override
    public Object probe$es() {
        return probe$es;
    }

    @Override
    public void probe$setEs(Object s) {
        probe$es = s;
    }
}
