package dev.lull.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exactness tests: the server's tick counter, set to a fixed value by TestSeeds.reseed - mobs pick goals on ticks
 * where (server tick count + id) is even, and the server ticks a run-dependent number of times before a scene (the
 * tools poll while the levels are frozen).
 */
@Mixin(MinecraftServer.class)
public interface SeedServerTickAccessor {
    @Accessor("tickCount")
    void sleep$setTickCount(int ticks);
}
