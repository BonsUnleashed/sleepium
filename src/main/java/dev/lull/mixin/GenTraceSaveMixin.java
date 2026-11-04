package dev.lull.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.lull.optsupport.GenTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Measurement only (-Dlab.pgen.trace=true): where a chunk save's time goes (ChunkSerializer.write's parts). */
@Mixin(ChunkSerializer.class)
public abstract class GenTraceSaveMixin {
    @WrapMethod(method = "write")
    private static CompoundTag sleep$write(ServerLevel level, ChunkAccess chunk, Operation<CompoundTag> original) {
        long t = System.nanoTime();
        try {
            return original.call(level, chunk);
        } finally {
            GenTrace.part(Thread.currentThread() == level.getServer().getRunningThread() ? "write (main)" : "write (other)", System.nanoTime() - t);
        }
    }

    @WrapOperation(method = "write", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LayerLightEventListener;getDataLayerData(Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;"))
    private static DataLayer sleep$light(LayerLightEventListener l, SectionPos pos, Operation<DataLayer> original) {
        long t = System.nanoTime();
        try {
            return original.call(l, pos);
        } finally {
            GenTrace.part("light", System.nanoTime() - t);
        }
    }

    @WrapOperation(method = "write", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getBlockEntityNbtForSaving(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag sleep$be(ChunkAccess c, BlockPos pos, Operation<CompoundTag> original) {
        long t = System.nanoTime();
        try {
            return original.call(c, pos);
        } finally {
            GenTrace.part("block entities", System.nanoTime() - t);
        }
    }

    @WrapMethod(method = "saveTicks")
    private static void sleep$ticks(ServerLevel level, CompoundTag tag, ChunkAccess.TicksToSave ticks, Operation<Void> original) {
        long t = System.nanoTime();
        try {
            original.call(level, tag, ticks);
        } finally {
            GenTrace.part("ticks", System.nanoTime() - t);
        }
    }

    @WrapMethod(method = "packStructureData")
    private static CompoundTag sleep$structures(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx,
                                                net.minecraft.world.level.ChunkPos pos,
                                                java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart> starts,
                                                java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> refs,
                                                Operation<CompoundTag> original) {
        long t = System.nanoTime();
        try {
            return original.call(ctx, pos, starts, refs);
        } finally {
            GenTrace.part("structures", System.nanoTime() - t);
        }
    }

    @WrapOperation(method = "write", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/LevelChunk;writeCapsToNBT()Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag sleep$attachments(net.minecraft.world.level.chunk.LevelChunk c, Operation<CompoundTag> original) {
        long t = System.nanoTime();
        try {
            return original.call(c);
        } finally {
            GenTrace.part("capabilities", System.nanoTime() - t);
        }
    }
}
