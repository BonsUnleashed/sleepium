package dev.probe.mixin;

import dev.probe.optsupport.sleep.SleepEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * Sleep engine: world reads no subscription can wake a sleeper for (fluids, light, weather, time of day, heightmaps).
 * During an observed tick each one marks the tick as "not just time passing", so a block entity depending on them never
 * falls asleep on them. Argument-taking methods use a pass-through @ModifyVariable (no CallbackInfo allocation on these
 * hot paths); the getters use MixinExtras return-value pass-throughs, also allocation-free. Sleep engine only.
 */
@Mixin(Level.class)
public abstract class SleepProbeUnobservedMixin {
    @ModifyVariable(method = "getFluidState", at = @At("HEAD"), argsOnly = true)
    private BlockPos probe$fluid(BlockPos pos) {
        SleepEngine.readPos('F', pos);   // a fluid is part of the block state: block changes at pos wake the sleeper
        return pos;
    }

    @ModifyVariable(method = "isRainingAt", at = @At("HEAD"), argsOnly = true)
    private BlockPos probe$rainAt(BlockPos pos) {
        SleepEngine.weatherRead((Level) (Object) this);
        return pos;
    }

    @ModifyVariable(method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I", at = @At("HEAD"), argsOnly = true)
    private Heightmap.Types probe$height(Heightmap.Types t) {
        SleepEngine.unobservedRead();
        return t;
    }

    @ModifyVariable(method = "getRainLevel", at = @At("HEAD"), argsOnly = true)
    private float probe$rainLevel(float d) {
        SleepEngine.weatherRead((Level) (Object) this);
        return d;
    }

    @ModifyVariable(method = "getThunderLevel", at = @At("HEAD"), argsOnly = true)
    private float probe$thunderLevel(float d) {
        SleepEngine.weatherRead((Level) (Object) this);
        return d;
    }

    // no-argument getters: pass-through return values (MixinExtras, no CallbackInfo allocation)
    @ModifyReturnValue(method = {"isRaining", "isThundering"}, at = @At("RETURN"))
    private boolean probe$weather(boolean v) {
        SleepEngine.weatherRead((Level) (Object) this);
        return v;
    }

    @ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
    private long probe$dayTime(long v) {
        SleepEngine.unobservedRead();
        return v;
    }

    @ModifyReturnValue(method = "getLightEngine", at = @At("RETURN"))
    private LevelLightEngine probe$light(LevelLightEngine v) {
        SleepEngine.unobservedRead();
        return v;
    }
}
