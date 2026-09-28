package dev.probe.opt;

import dev.probe.optsupport.LightNotifyRing;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * ServerReactor (light notifications): onLightUpdate notes the section in a LightNotifyRing; one drain task on the
 * same main-thread executor vanilla uses applies every pending section (vanilla's body, once per section).
 */
@Mixin(ServerChunkCache.class)
public abstract class MainReactorLightNotifyMixin {
    @Shadow
    @Nullable
    protected abstract ChunkHolder getVisibleChunkIfPresent(long chunkPos);

    @Unique private final LightNotifyRing probe$ring = new LightNotifyRing();
    @Unique private BlockableEventLoop<Runnable> probe$executor;
    @Unique private final Runnable probe$drain = this::probe$drain;

    @Unique
    @SuppressWarnings("unchecked")
    private BlockableEventLoop<Runnable> probe$executor() {
        BlockableEventLoop<Runnable> e = probe$executor;
        if (e == null) probe$executor = e = (BlockableEventLoop<Runnable>) dev.probe.optsupport.Reflect.get(this, "mainThreadProcessor");
        return e;
    }

    /**
     * @author probe
     * @reason one drain task per batch of light notifications instead of one task per section (see LightNotifyRing)
     */
    @Overwrite
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        if (probe$ring.note(type == LightLayer.SKY, pos.asLong())) probe$executor().execute(probe$drain);
    }

    @Unique
    private void probe$drain() {
        probe$ring.drain(p -> probe$apply(LightLayer.SKY, p), p -> probe$apply(LightLayer.BLOCK, p));
    }

    @Unique
    private void probe$apply(LightLayer type, long p) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(ChunkPos.asLong(SectionPos.x(p), SectionPos.z(p)));
        if (chunkholder != null) {
            chunkholder.sectionLightChanged(type, SectionPos.y(p));
        }
    }
}
