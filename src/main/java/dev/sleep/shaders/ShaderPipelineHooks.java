package dev.sleep.shaders;

/** Calls into the shader mixins' accessors (kept out of ShaderPipeline so it loads without them). */
final class ShaderPipelineHooks {
    private ShaderPipelineHooks() {}

    /** ShaderInstance forgets which program it thinks is in use */
    static void resetProgramCache() {
        dev.sleep.shadermixin.ShaderProgramCacheAccessor.sleep$setLastProgramId(-1);
        dev.sleep.shadermixin.ShaderProgramCacheAccessor.sleep$setLastAppliedShader(null);
    }
}
