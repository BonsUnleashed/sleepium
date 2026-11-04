package dev.lull.opt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;

/**
 * exp/client-event-sites (client): EventSiteMixin's carrier for the client. The frame and render events (GUI overlays'
 * Pre / Post per overlay, render stages, fog, FOV, camera, hands) and the per-entity ones (name tag, living, player)
 * are posted from these classes; each MinecraftForge.EVENT_BUS.post(new E(...)) becomes an EventSites call site, so an
 * event with no listener is not built. A listener anywhere switches the site back to the full post at once.
 */
@Mixin(value = {ForgeHooksClient.class, ForgeGui.class, EntityRenderer.class, LivingEntityRenderer.class, PlayerRenderer.class,
        ItemFrameRenderer.class, Minecraft.class, ClientLevel.class}, remap = false)
public abstract class ClientEventSiteMixin {
}
