package dev.lull.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exactness tests: the entity id counter, set to a fixed base by TestSeeds.reseed (entity seeds and staggers read the id). */
@Mixin(Entity.class)
public interface SeedEntityCounterAccessor {
    @Accessor("ENTITY_COUNTER")
    static AtomicInteger sleep$counter() {
        throw new AssertionError();
    }
}
