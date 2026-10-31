package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.GenProfile;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (genbench): scheduled block and fluid ticks run by the level (all chunks, new ones included). */
@Mixin(LevelTicks.class)
public abstract class GenProfileTicksMixin<T> {
    @WrapMethod(method = "tick")
    private void probe$tick(long gameTime, int maxAllowedTicks, BiConsumer<BlockPos, T> ticker, Operation<Void> original) {
        if (!GenProfile.active()) {
            original.call(gameTime, maxAllowedTicks, ticker);
            return;
        }
        long t = System.nanoTime(), m = GenProfile.allocated();
        try {
            original.call(gameTime, maxAllowedTicks, ticker);
        } finally {
            GenProfile.add("main: scheduled ticks (LevelTicks)", System.nanoTime() - t, GenProfile.allocated() - m);
        }
    }
}
