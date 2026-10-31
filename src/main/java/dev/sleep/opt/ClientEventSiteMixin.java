package dev.sleep.opt;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.gui.GuiLayerManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * exp/client-event-sites (client): EventSiteMixin's carrier for the client. The frame and render events (about 70 per
 * frame: GUI layers, render stages, fog, FOV, camera, hands) and the per-entity ones (name tag, living, player) are
 * posted from these classes; each NeoForge.EVENT_BUS.post(new E(...)) becomes an EventSites call site, so an event
 * with no listener is not built. A listener anywhere switches the site back to the full post at once.
 */
@Mixin({ClientHooks.class, GuiLayerManager.class, Camera.class, EntityRenderer.class, LivingEntityRenderer.class, PlayerRenderer.class,
        ItemFrameRenderer.class, Gui.class, Minecraft.class, ClientLevel.class})
public abstract class ClientEventSiteMixin {
}
