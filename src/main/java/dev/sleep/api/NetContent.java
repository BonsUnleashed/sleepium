package dev.sleep.api;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Large content from the server to clients (definitions, generated data, anything over a few KB that clients need),
 * sent by what it contains: each entry travels once per client and is kept in the client's cache by its SHA-256, a
 * change sends only the changed entries, a transfer cut by a disconnect resumes, and chunks go only as fast as the
 * connection drains them (combat packets never wait behind megabytes). With {@link Feature#NET_BULK} off, or for a
 * client without probe, or in singleplayer, {@link #sendTo} returns false: send your content your own way then.
 *
 * <pre>{@code
 * // both sides, once (mod construction): what the client does with a complete set, on its main thread
 * NetContent.register(MY_SET, entries -> MyData.load(entries));
 * // server, on load and after every reload
 * NetContent.publish(MY_SET, Map.of("a.json", bytesA, "b.json", bytesB));
 * // server, a player joined
 * if (!NetContent.sendTo(player, MY_SET)) PacketDistributor.sendToPlayer(player, new MyFullPayload(...));
 * }</pre>
 */
public final class NetContent {
    private NetContent() {}

    public static void register(ResourceLocation set, Consumer<Map<String, byte[]>> clientApply) {
        dev.sleep.net.Bulk.register(set, clientApply);
    }

    /** any thread: the set's new content (built off the calling thread; clients get a new manifest) */
    public static void publish(ResourceLocation set, Map<String, byte[]> entries) {
        dev.sleep.net.Bulk.publish(set, entries);
    }

    /** server thread: the set to a (joining) player; false = not served this way */
    public static boolean sendTo(ServerPlayer player, ResourceLocation set) {
        return dev.sleep.net.Bulk.sendTo(player, set);
    }

    /** whether this player gets content sets through here (for choosing the fallback after a publish) */
    public static boolean served(ServerPlayer player) {
        return dev.sleep.net.Bulk.capable(player);
    }
}
