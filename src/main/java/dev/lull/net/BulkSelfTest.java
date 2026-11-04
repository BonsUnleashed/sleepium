package dev.lull.net;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code /sleep bulktest <entries> <kb> <changed> <seed>}: a synthetic content set {@code probe:selftest} for testing
 * the bulk transfer without touching real content. Entry i is {@code kb} KB of text-like bytes; the first
 * {@code changed} entries depend on {@code seed}, the rest never change — so a second publish with another seed is a
 * delta of {@code changed} entries. The client logs a digest of what it received (same line on every client).
 */
public final class BulkSelfTest {
    public static final ResourceLocation SET = ResourceLocation.fromNamespaceAndPath(dev.lull.SleepMod.MOD_ID, "selftest");

    private BulkSelfTest() {}

    public static void register() {
        Bulk.register(SET, m -> {
            long d = 0, bytes = 0;
            for (var e : new java.util.TreeMap<>(m).entrySet()) {
                d = d * 31 + java.util.Arrays.hashCode(e.getValue()) + e.getKey().hashCode();
                bytes += e.getValue().length;
            }
            System.out.println("[sleep/net] selftest received: " + m.size() + " entries, " + bytes / 1024 + " KB, digest " + Long.toHexString(d));
        });
    }

    /** server: publish the synthetic set; returns the digest the clients should log */
    public static String publish(int entries, int kb, int changed, long seed) {
        Map<String, byte[]> m = new HashMap<>();
        for (int i = 0; i < entries; i++) m.put(String.format("e%04d", i), content(i, kb, i < changed ? seed : 0));
        Bulk.publish(SET, m);
        long d = 0, bytes = 0;
        for (var e : new java.util.TreeMap<>(m).entrySet()) {
            d = d * 31 + java.util.Arrays.hashCode(e.getValue()) + e.getKey().hashCode();
            bytes += e.getValue().length;
        }
        return entries + " entries, " + bytes / 1024 + " KB, digest " + Long.toHexString(d);
    }

    /** text-like, compressible about as well as the controller JSON */
    private static byte[] content(int i, int kb, long seed) {
        java.util.Random r = new java.util.Random(i * 1_000_003L + seed * 7919L);
        String[] words = {"\"state\":", "\"t\":", "0.25,", "\"bone\":", "[1.0,0.0,0.0],", "\"hit\":", "true,", "\"r1_1\"", "{", "}", "\"next\":"};
        StringBuilder b = new StringBuilder(kb * 1024);
        while (b.length() < kb * 1024) {
            b.append(words[r.nextInt(words.length)]);
            if (r.nextInt(4) == 0) b.append(r.nextInt(1000));
        }
        b.setLength(kb * 1024);
        return b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
