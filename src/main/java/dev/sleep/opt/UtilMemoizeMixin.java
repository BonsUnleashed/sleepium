package dev.sleep.opt;

import dev.sleep.optsupport.Memo2;
import java.util.function.BiFunction;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** exp/frame-garbage: Util.memoize(BiFunction) without a Pair and a lambda per call (Memo2). */
@Mixin(Util.class)
public abstract class UtilMemoizeMixin {
    /**
     * @author probe
     * @reason the same memoisation without two allocations per call (see Memo2)
     */
    @Overwrite
    public static <T, U, R> BiFunction<T, U, R> memoize(BiFunction<T, U, R> memoBiFunction) {
        return new Memo2<>(memoBiFunction);
    }
}
