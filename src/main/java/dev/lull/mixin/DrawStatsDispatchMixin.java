package dev.lull.mixin;

import dev.lull.DrawStats;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** -Dsleep.drawStats: the parts of an entity's dispatch (the dispatcher's render, the light, the shadow). */
@Mixin(EntityRenderDispatcher.class)
public abstract class DrawStatsDispatchMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sleep$renderBegin(CallbackInfo ci) {
        DrawStats.phaseBegin(5);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sleep$renderEnd(CallbackInfo ci) {
        DrawStats.phaseEnd(5);
    }

    @Inject(method = "getPackedLightCoords", at = @At("HEAD"))
    private void sleep$lightBegin(CallbackInfoReturnable<Integer> ci) {
        DrawStats.phaseBegin(6);
    }

    @Inject(method = "getPackedLightCoords", at = @At("RETURN"))
    private void sleep$lightEnd(CallbackInfoReturnable<Integer> ci) {
        DrawStats.phaseEnd(6);
    }

    @Inject(method = "renderShadow", at = @At("HEAD"))
    private static void sleep$shadowBegin(CallbackInfo ci) {
        DrawStats.phaseBegin(7);
    }

    @Inject(method = "renderShadow", at = @At("RETURN"))
    private static void sleep$shadowEnd(CallbackInfo ci) {
        DrawStats.phaseEnd(7);
    }
}
