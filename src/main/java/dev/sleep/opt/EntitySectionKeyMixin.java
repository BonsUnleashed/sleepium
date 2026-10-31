package dev.sleep.opt;

import dev.sleep.optsupport.IndexedSection;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every new EntitySection learns its section key (the grid origin of its index). */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionKeyMixin {
    @Inject(method = "createSection", at = @At("RETURN"))
    private void probe$key(long sectionPos, CallbackInfoReturnable<EntitySection<?>> cir) {
        ((IndexedSection) cir.getReturnValue()).probe$setKey(sectionPos);
    }
}
