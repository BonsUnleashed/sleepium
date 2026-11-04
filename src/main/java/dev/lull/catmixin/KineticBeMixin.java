package dev.lull.catmixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticEffectHandler;
import dev.lull.optsupport.KineticMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** KineticMemo: a kinetic block entity's overstress tint state, part of what its memo is valid for; KineticResident: its draw in VRAM. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public abstract class KineticBeMixin implements KineticMemo.Effects {
    @Shadow
    protected KineticEffectHandler effects;

    @org.spongepowered.asm.mixin.Unique
    private Object sleep$resident;
    @org.spongepowered.asm.mixin.Unique
    private int sleep$version;

    @Override
    public int sleep$version() {
        return sleep$version;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "read", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void sleep$read(net.minecraft.nbt.CompoundTag tag, boolean clientPacket,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {   // (1.20.1: no registries argument)
        sleep$version++;
    }

    @Override
    public Object sleep$resident() {
        return sleep$resident;
    }

    @Override
    public void sleep$resident(Object r) {
        sleep$resident = r;
    }

    @Override
    public float sleep$overStressed() {
        return effects == null ? 0f : ((OverStressAccessor) effects).sleep$overStressed();
    }
}
