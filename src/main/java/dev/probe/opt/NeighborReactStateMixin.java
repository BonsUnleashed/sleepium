package dev.probe.opt;

import dev.probe.optsupport.NeighborReact;
import dev.probe.optsupport.ReactsHolder;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Per-state cache of NeighborReact (computed on first use). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class NeighborReactStateMixin implements ReactsHolder {
    @Unique private byte probe$reacts = -1;

    @Override
    public boolean probe$reacts() {
        byte r = probe$reacts;
        if (r < 0) {
            r = (byte) (NeighborReact.reactsSlow((BlockState) (Object) this) ? 1 : 0);
            probe$reacts = r;
        }
        return r == 1;
    }
}
