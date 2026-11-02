package dev.lull.shaders;

/** Calls into the shader mixins' accessors (kept out of ShaderPipeline so it loads without them). */
final class ShaderPipelineHooks {
    private ShaderPipelineHooks() {}

    /** ShaderInstance forgets which program it thinks is in use */
    static void resetProgramCache() {
        dev.lull.shadermixin.ShaderProgramCacheAccessor.sleep$setLastProgramId(-1);
        dev.lull.shadermixin.ShaderProgramCacheAccessor.sleep$setLastAppliedShader(null);
    }
}
