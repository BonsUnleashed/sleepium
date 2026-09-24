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
        TickTimer.INSTANCE.register(gameBus);
        EventCounter.INSTANCE.attach(gameBus);
        EventBench.registerListeners(gameBus);
        gameBus.addListener(ProbeCommand::register);
        if (FMLEnvironment.dist.isClient()) {
            ClientProbe.register(gameBus);   // class is only loaded on the physical client
        }
    }
}
