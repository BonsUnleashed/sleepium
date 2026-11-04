package dev.lull.opt;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;

/**
 * exp/event-sites: carrier for EventSiteTransformer (run from OptMixinPlugin.postApply on these classes): every
 * MinecraftForge.EVENT_BUS.post(new E(...)) in Forge's hook classes - and in Entity / LivingEntity, whose 1.20.1
 * patches post some directly - becomes an EventSites call site, so an event nobody listens to costs nothing, not even
 * its allocation. Listeners (vanilla, Forge or any mod) switch it back at once.
 */
@Mixin(value = {ForgeEventFactory.class, ForgeHooks.class, Entity.class, LivingEntity.class}, remap = false)
public abstract class EventSiteMixin {
}
