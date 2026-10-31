package dev.sleep.opt;

import dev.sleep.optsupport.SignalProof;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Goes with SignalPathMixin: the per-state proof flags. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SignalProofStateMixin implements SignalProof.Flags {
    @Unique private byte sleep$sig, sleep$dir;

    @Override public byte sleep$sigFlag() { return sleep$sig; }
    @Override public void sleep$setSigFlag(byte v) { sleep$sig = v; }
    @Override public byte sleep$dirFlag() { return sleep$dir; }
    @Override public void sleep$setDirFlag(byte v) { sleep$dir = v; }
}
