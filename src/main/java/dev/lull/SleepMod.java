package dev.lull;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/** Sleepium on Minecraft 1.20.1 / Forge: the port's entry point (the engine comes over from ../sleep step by step). */
@Mod(SleepMod.MOD_ID)
public final class SleepMod {
    public static final String MOD_ID = "sleepium";
    /** a dev run (not a built jar in a launcher): the tools' old names stay too (/sleep, /sleepclient) */
    public static final boolean DEV = !FMLEnvironment.production;
    static final Logger LOGGER = LogUtils.getLogger();

    public SleepMod() {
        IEventBus gameBus = MinecraftForge.EVENT_BUS;
        if (Boolean.getBoolean("lab.opt.HeapReturn")) LOGGER.info("sleep: {}", dev.lull.optsupport.HeapReturn.apply());   // memory back to the OS after bursts
        // classes the game loads only while played, learned and preloaded in the background (lab.opt.ClassPreload)
        gameBus.addListener(EventPriority.NORMAL, false, net.minecraftforge.event.server.ServerAboutToStartEvent.class, e -> dev.lull.optsupport.ClassPreload.preload());
        gameBus.addListener(EventPriority.NORMAL, false, net.minecraftforge.event.server.ServerStartedEvent.class, e -> dev.lull.optsupport.ClassPreload.started());
        gameBus.addListener(EventPriority.NORMAL, false, net.minecraftforge.event.server.ServerStoppedEvent.class, e -> dev.lull.optsupport.ClassPreload.stopped());
        TickTimer.INSTANCE.register(gameBus);
        gameBus.addListener(SleepCommand::register);
        gameBus.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, e -> {
            if (e.phase == TickEvent.Phase.END) dev.lull.api.Travel.tick();   // 1.21.1: ServerTickEvent.Post
        });
        // not yet on 1.20.1: EventCounter, EventBench (the event bus step), NetBench, the bulk payloads / coalescing (networking),
        // GenBench (world generation), the settings screen and the shaderpack pipeline (port-pending.txt)
        if (FMLEnvironment.dist.isClient()) {
            ClientMeter.register(gameBus);   // class is only loaded on the physical client
            // the render cache's frame budget (1.21.1: RenderFrameEvent.Pre; Forge 1.20.1: RenderTickEvent START)
            gameBus.addListener(EventPriority.HIGHEST, false, TickEvent.RenderTickEvent.class, e -> {
                if (e.phase == TickEvent.Phase.START) dev.lull.optsupport.RenderCache.frame();
            });
        }
        System.out.println("[sleep] Sleepium for 1.20.1 / Forge loaded");
    }
}
