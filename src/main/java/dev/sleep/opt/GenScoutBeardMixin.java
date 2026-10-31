package dev.sleep.opt;

import dev.sleep.optsupport.GenScout;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measurement only (lab.genscout): how many pieces/junctions each chunk's Beardifier walks per block. */
@Mixin(Beardifier.class)
public abstract class GenScoutBeardMixin {
    @Shadow @Final protected ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow @Final protected ObjectListIterator<JigsawJunction> junctionIterator;

    @Inject(method = "forStructuresInChunk", at = @At("RETURN"))
    private static void sleep$sizes(StructureManager sm, ChunkPos pos, CallbackInfoReturnable<Beardifier> cir) {
        GenScoutBeardMixin b = (GenScoutBeardMixin) (Object) cir.getReturnValue();
        int r = 0, j = 0;
        while (b.pieceIterator.hasNext()) { b.pieceIterator.next(); r++; }
        b.pieceIterator.back(Integer.MAX_VALUE);
        while (b.junctionIterator.hasNext()) { b.junctionIterator.next(); j++; }
        b.junctionIterator.back(Integer.MAX_VALUE);
        GenScout.BEARD_CHUNKS.increment();
        if (r + j > 0) {
            GenScout.BEARD_NONEMPTY.increment();
            GenScout.BEARD_RIGIDS.add(r);
            GenScout.BEARD_JUNCTIONS.add(j);
        }
    }

    @Inject(method = "compute", at = @At("HEAD"))
    private void sleep$call(DensityFunction.FunctionContext ctx, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        GenScout.BEARD_CALLS.increment();
    }
}
