package dev.sleep.api;

/**
 * probe's switchable features, as {@link ProbeApi#isEnabled(Feature)} reports them. The flag is the JVM property
 * {@code -Dlab.opt.<flag>=true}; features {@link #inAll() in the "all" set} are also on with {@code -Dlab.opt.all=true},
 * and {@code -Dlab.opt.universal=true} has already been expanded into its individual flags when mods load.
 */
public enum Feature {
    /** the server thread sleeps until work or the next tick instead of polling every 100 µs */
    MAIN_REACTOR("MainReactor", true),
    /** generation tasks on the pool, FULL through a feedback ring, saved chunks read on the pool */
    WORLDGEN_REACTOR("WorldgenReactor", true),
    /** SURFACE / CARVERS / FEATURES and structure starts on the pool */
    PARALLEL_WORLDGEN("ParallelWorldgenMixin", true),
    /** vanilla's worker and IO pools replaced by LabRuntime */
    LAB_POOLS("LabPoolsMixin", true),
    /** palette encoding of saved chunks on the pool */
    ASYNC_CHUNK_SAVE("AsyncChunkSaveMixin", true),
    /** NeoForge event bus: generated dispatch for long listener lists */
    EVENT_BUS("NeoForgeBusMixin", true),
    /** block entities whose tick only lets time pass stop ticking until something they read changes */
    BLOCK_ENTITY_SLEEP("SleepMixin", false),
    /** resting entities replay a proven quiet period instead of ticking */
    ENTITY_SLEEP("EntitySleep", false),
    /** client: entities rendered on worker threads, merged in entity order */
    PARALLEL_ENTITIES("ParallelEntitiesMixin", true),
    /** client: a renderer's output recorded once and replayed while its state is unchanged */
    RENDER_CACHE("RenderCacheMixin", true),
    /** client: chunk sections drawn with multi-draw-indirect from shared arenas */
    CHUNK_MULTI_DRAW("ChunkMultiDrawMixin", true),
    /** client: light for chunk sections computed in the shader from light volumes */
    SHADER_LIGHT("ShaderLightMixin", true),
    /** client: light updates as a pool job that may span frames */
    CLIENT_LIGHT_ASYNC("ClientLightAsyncMixin", true),
    /** server: a flush's packets per connection go to netty as one task with one socket write (every packet, any mod) */
    NET_REACTOR("NetReactor", true),
    /** content sets sent by content hash: client cache, deltas, resume, transport-paced (see NetContent) */
    NET_BULK("NetBulk", true),
    /** server → probe clients: a flush's frames as one container of a per-connection deflate stream */
    NET_COALESCE("NetCoalesce", true);

    private final String flag;
    private final boolean inAll;

    Feature(String flag, boolean inAll) {
        this.flag = flag;
        this.inAll = inAll;
    }

    /** the name after {@code lab.opt.} */
    public String flag() {
        return flag;
    }

    /** whether {@code -Dlab.opt.all=true} turns it on */
    public boolean inAll() {
        return inAll;
    }
}
