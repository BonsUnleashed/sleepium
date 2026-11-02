package dev.lull.mixin;

import dev.lull.DrawStats;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** -Dsleep.drawStats: render-thread time in each of renderLevel's profiler sections (entities, block entities, terrain...). */
@Mixin(LevelRenderer.class)
public abstract class DrawStatsPhasesMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sleep$begin(CallbackInfo ci) {
        DrawStats.section("start");
    }

    @ModifyArg(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V"))
    private String sleep$section(String name) {
        DrawStats.section(name);
        return name;
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void sleep$end(CallbackInfo ci) {
        DrawStats.section(null);
    }
}
