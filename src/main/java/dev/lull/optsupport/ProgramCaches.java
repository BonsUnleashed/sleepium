package dev.lull.optsupport;

/**
 * The engine's per-program caches (uniform locations, block bindings) keyed by GL program id: a deleted program's id
 * can be handed out again for another program, so whoever deletes programs (a shaderpack unloaded) forgets them here.
 */
public final class ProgramCaches {
    private ProgramCaches() {}

    public static void forget(int program) {
        ChunkMultiDraw.forgetProgram(program);
        LightVolumes.forgetProgram(program);
        Materials.forgetProgram(program);
        PartBatch.forgetProgram(program);
    }
}
