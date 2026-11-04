package dev.lull.opt;

import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The section a server entity currently lives in. */
@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public interface EntityCallbackAccessor {
    @Accessor("currentSection")
    EntitySection<?> sleep$section();
}
