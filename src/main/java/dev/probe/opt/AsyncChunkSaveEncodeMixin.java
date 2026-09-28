package dev.probe.opt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import dev.probe.optsupport.AsyncChunkSave;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** AsyncChunkSaveMixin: ChunkSerializer.write's encodes (palettes deferred while a save is deferred, the rest as is). */
@Mixin(ChunkSerializer.class)
public abstract class AsyncChunkSaveEncodeMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "write", at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
    private static DataResult probe$encode(Codec codec, DynamicOps ops, Object value) {
        return AsyncChunkSave.encode(codec, ops, value);
    }
}
