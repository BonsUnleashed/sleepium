package dev.sleep.geomixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.loading.object.BakedAnimations;

/** GeckoLib's animation files read as a stream, an animation at a time (dev.sleep.optsupport.AnimStream) */
@Mixin(targets = "software.bernie.geckolib.loading.FileLoader", remap = false)
public abstract class AnimStreamMixin {
    @Inject(method = "loadAnimationsFile", at = @At("HEAD"), cancellable = true)
    private static void sleep$stream(ResourceLocation location, ResourceManager manager, CallbackInfoReturnable<BakedAnimations> cir) {
        if (!location.getPath().endsWith(".animation.json")) return;   // (GeckoLib's own checks and messages for the rest)
        try {
            cir.setReturnValue(dev.sleep.optsupport.AnimStream.load(location, manager));
        } catch (Exception e) {
            System.out.println("[sleep/opt] animation stream " + location + ": " + e + " - GeckoLib's own load");
        }
    }
}
