package dev.sleep.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.sleep.IoScout;
import java.nio.ByteBuffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;

/** Measurement only (-Dsleep.ioScout=true): the disk part of a chunk write (sectors + header) and the header alone. */
@Mixin(RegionFile.class)
public abstract class IoScoutRegionMixin {
    @WrapMethod(method = "write")
    private void sleep$write(ChunkPos pos, ByteBuffer data, Operation<Void> original) {
        long t = System.nanoTime();
        try {
            original.call(pos, data);
        } finally {
            IoScout.disk(System.nanoTime() - t);
        }
    }

    @WrapMethod(method = "writeHeader")
    private void sleep$header(Operation<Void> original) {
        long t = System.nanoTime();
        try {
            original.call();
        } finally {
            IoScout.header(System.nanoTime() - t);
        }
    }
}
