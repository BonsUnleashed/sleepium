package dev.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.probe.GenProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (genbench): SectionStorage (POI) loading a column — tryRead(pos).join() on the calling thread. */
@Mixin(SectionStorage.class)
public abstract class GenProfilePoiReadMixin {
    @WrapMethod(method = "readColumn(Lnet/minecraft/world/level/ChunkPos;)V")
    private void probe$readColumn(ChunkPos pos, Operation<Void> original) {
        if (!GenProfile.active()) { original.call(pos); return; }
        long t = System.nanoTime(), m = GenProfile.allocated();
        original.call(pos);
        GenProfile.add((Thread.currentThread().getName().startsWith("Server thread") ? "main: (inside read) " : "(inside) ")
                + "section storage readColumn (IO join)", System.nanoTime() - t, GenProfile.allocated() - m);
    }
}
