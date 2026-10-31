package dev.sleep.net.client;

import dev.sleep.api.Pools;
import dev.sleep.net.Bulk;
import dev.sleep.net.BulkPayloads.Chunk;
import dev.sleep.net.BulkPayloads.Entry;
import dev.sleep.net.BulkPayloads.Manifest;
import dev.sleep.net.BulkPayloads.Want;
import dev.sleep.net.SerialExecutor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Client side of the bulk transfer: a disk cache of entries by hash (`probe-cache/bulk/<sha-256>`, the packed bytes),
 * partial entries as `<hash>.part` (a resume starts after the last whole chunk), every entry's hash checked after
 * inflating, the complete set handed to the content's consumer on the main thread. All file work runs on one serial
 * executor over the IO pool, in arrival order — never on the network or main thread.
 */
public final class BulkClient {
    private static final SerialExecutor IO = new SerialExecutor(Pools.io());
    private static final HexFormat HEX = HexFormat.of();
    /** IO executor only */
    private static final Map<ResourceLocation, Pending> CURRENT = new HashMap<>();
    private static final Map<String, FileChannel> OPEN = new HashMap<>();
    private static final Map<String, ResourceLocation> OWNER = new HashMap<>();

    private record Pending(Manifest manifest, long start, long[] received) {}

    private BulkClient() {}

    private static Path dir() {
        return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("probe-cache").resolve("bulk");
    }

    /** reply: sends on the connection the manifest came by (the client's player may not exist yet at login) */
    public static void onManifest(Manifest m, java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> reply) {
        IO.execute(() -> {
            try {
                Files.createDirectories(dir());
                List<byte[]> want = new ArrayList<>();
                List<Integer> from = new ArrayList<>();
                int cached = 0;
                for (Entry e : m.entries()) {
                    String hex = HEX.formatHex(e.hash());
                    Path f = dir().resolve(hex), part = dir().resolve(hex + ".part");
                    if (Files.exists(f) && Files.size(f) == e.packedSize()) {
                        cached++;
                        continue;
                    }
                    int have = 0;
                    if (Files.exists(part)) {
                        have = (int) Math.min(e.chunks(), Files.size(part) / m.chunkSize());
                        try (FileChannel fc = FileChannel.open(part, StandardOpenOption.WRITE)) {
                            fc.truncate((long) have * m.chunkSize());   // a torn last chunk is fetched again
                        }
                    }
                    want.add(e.hash());
                    from.add(have);
                    OWNER.put(hex, m.set());
                }
                CURRENT.put(m.set(), new Pending(m, System.nanoTime(), new long[1]));
                System.out.println("[probe/net] bulk " + m.set() + " v" + m.version() + ": " + m.entries().size() + " entries, " + cached
                        + " from the cache, asking for " + want.size());
                reply.accept(new Want(m.set(), m.version(), cached, want, from));
                if (want.isEmpty()) complete(m.set());
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
    }

    public static void onChunk(Chunk c) {
        IO.execute(() -> {
            String hex = HEX.formatHex(c.hash());
            ResourceLocation set = OWNER.get(hex);
            Pending p = set == null ? null : CURRENT.get(set);
            if (p == null) return;   // not asked for in the current manifest
            try {
                FileChannel fc = OPEN.get(hex);
                if (fc == null) OPEN.put(hex, fc = FileChannel.open(dir().resolve(hex + ".part"), StandardOpenOption.CREATE, StandardOpenOption.WRITE));
                fc.write(ByteBuffer.wrap(c.data()), (long) c.index() * p.manifest().chunkSize());
                p.received()[0] += c.data().length;
                if (c.index() == c.count() - 1) {
                    fc.close();
                    OPEN.remove(hex);
                    OWNER.remove(hex);
                    Files.move(dir().resolve(hex + ".part"), dir().resolve(hex), StandardCopyOption.REPLACE_EXISTING);
                    if (!OWNER.containsValue(set)) complete(set);
                }
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
    }

    /** IO executor: every entry of the current manifest is on disk; inflate, check, hand over */
    private static void complete(ResourceLocation set) {
        Pending p = CURRENT.get(set);
        if (p == null) return;
        for (Entry e : p.manifest().entries())   // a newer manifest may still wait for some of its entries
            if (!Files.exists(dir().resolve(HEX.formatHex(e.hash())))) return;
        CURRENT.remove(set);
        Map<String, byte[]> out = new HashMap<>();
        for (Entry e : p.manifest().entries()) {
            String hex = HEX.formatHex(e.hash());
            Path f = dir().resolve(hex);
            try {
                byte[] raw;
                try (var z = new java.util.zip.InflaterInputStream(Files.newInputStream(f))) {
                    raw = z.readAllBytes();
                }
                byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(raw);
                if (!java.util.Arrays.equals(h, e.hash())) {
                    Files.deleteIfExists(f);   // a damaged cache file: the next manifest fetches it again
                    System.out.println("[probe/net] bulk " + set + ": entry " + e.name() + " failed its hash check; dropped");
                    return;
                }
                out.put(e.name(), raw);
            } catch (Exception ex) {
                System.out.println("[probe/net] bulk " + set + ": entry " + e.name() + " unreadable: " + ex);
                return;
            }
        }
        double ms = (System.nanoTime() - p.start()) / 1e6;
        System.out.println(String.format(java.util.Locale.ROOT, "[probe/net] bulk %s v%d complete: %d entries, %d KB received, %.0f ms after the manifest",
                set, p.manifest().version(), out.size(), p.received()[0] / 1024, ms));
        var consumer = Bulk.consumerFor(set);
        if (consumer != null) Minecraft.getInstance().execute(() -> consumer.accept(out));
    }
}
