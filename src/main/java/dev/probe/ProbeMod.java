package dev.probe;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Measurement probe. Everything is driven by the {@code /probe} command:
 * <ul>
 *   <li>{@code /probe start}  – reset tick stats, start counting every event type posted on the game bus</li>
 *   <li>{@code /probe status} – short summary in chat</li>
 *   <li>{@code /probe stop}   – stop counting, detach counting listeners, write report to {@code <gamedir>/probe/}</li>
 *   <li>{@code /probe bench [n]} – microbenchmark of {@code IEventBus.post} inside the live game</li>
 *   <li>{@code /probe world}  – entity / block entity / chunk counts per level</li>
 * </ul>
 */
@Mod(ProbeMod.MOD_ID)
public final class ProbeMod {
    public static final String MOD_ID = "probe";
    static final Logger LOGGER = LogUtils.getLogger();

    public ProbeMod(IEventBus modBus) {
        IEventBus gameBus = NeoForge.EVENT_BUS;
        // Which layer/loader owns the bus? Mixin can only transform classes of the GAME layer (TransformingClassLoader).
        LOGGER.info("probe: bus class {} module {} loader {}; game class loader {}", gameBus.getClass().getName(),
                gameBus.getClass().getModule(), gameBus.getClass().getClassLoader(),
                net.minecraft.server.MinecraftServer.class.getClassLoader());
        TickTimer.INSTANCE.register(gameBus);
        EventCounter.INSTANCE.attach(gameBus);
        EventBench.registerListeners(gameBus);
        gameBus.addListener(ProbeCommand::register);
        // network engine (dev.probe.net, notes/34)
        gameBus.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre.class, e -> NetBench.onTickStart());
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class, dev.probe.net.BulkPayloads::register);
        gameBus.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, e -> dev.probe.net.Bulk.pump(e.getServer()));
        gameBus.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) dev.probe.net.Bulk.onLogout(sp);
        });
        gameBus.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) dev.probe.net.Coalesce.enable(sp);   // before anything big goes out
        });
        dev.probe.net.BulkSelfTest.register();
        gameBus.addListener(net.neoforged.neoforge.event.level.ChunkEvent.Load.class, GenBench::onChunkLoad);
        if (FMLEnvironment.dist.isClient()) {
            ClientProbe.register(gameBus);   // class is only loaded on the physical client
        }
    }
}
