package dev.sleep.shadermixin;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** ShaderInstance's cache of the program in use (after raw glUseProgram calls it must be forgotten). */
@Mixin(ShaderInstance.class)
public interface ShaderProgramCacheAccessor {
    @Accessor("lastProgramId")
    static void sleep$setLastProgramId(int id) {
        throw new AssertionError();
    }

    @Accessor("lastAppliedShader")
    static void sleep$setLastAppliedShader(ShaderInstance s) {
        throw new AssertionError();
    }
}
