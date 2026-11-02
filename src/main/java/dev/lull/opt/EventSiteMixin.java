package dev.lull.opt;

import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * exp/event-sites: carrier for EventSiteTransformer (run from OptMixinPlugin.postApply on these classes): every
 * NeoForge.EVENT_BUS.post(new E(...)) in NeoForge's hook classes becomes an EventSites call site, so an event nobody
 * listens to costs nothing, not even its allocation. Listeners (vanilla, NeoForge or any mod) switch it back at once.
 */
@Mixin({EventHooks.class, CommonHooks.class})
public abstract class EventSiteMixin {
}
