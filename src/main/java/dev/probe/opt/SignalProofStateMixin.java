package dev.probe.opt;

import dev.probe.optsupport.SignalProof;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Goes with SignalPathMixin: the per-state proof flags. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SignalProofStateMixin implements SignalProof.Flags {
    @Unique private byte probe$sig, probe$dir;

    @Override public byte probe$sigFlag() { return probe$sig; }
    @Override public void probe$setSigFlag(byte v) { probe$sig = v; }
    @Override public byte probe$dirFlag() { return probe$dir; }
    @Override public void probe$setDirFlag(byte v) { probe$dir = v; }
}
