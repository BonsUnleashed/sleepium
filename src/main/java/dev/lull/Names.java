package dev.lull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minecraft's member names at run time. Forge 1.20.1 runs Mojang's class names with SRG member names (m_12345_,
 * f_12345_) in production, and Mojang's names in a dev run. Mixins are remapped by their refmap; the engine's own
 * bytecode work and reflection go through here: names are written as Mojang's in the code, turned into the running
 * ones (runtime) or read back from running bytecode (official). The table is the build's namedToIntermediate.tsrg,
 * shipped gzipped as sleepium-names.tsrg.gz, loaded on the first lookup in production only.
 */
public final class Names {
    private Names() {}

    /** true in production (SRG member names) */
    public static final boolean SRG = net.minecraftforge.fml.loading.FMLEnvironment.production;

    /** "owner.name(desc)" or "owner.name" (official) -> SRG; SRG -> official name */
    private static Map<String, String> toSrg, toOfficial;
    /** official "name(desc)" -> SRG, when every owner that declares it agrees (owner-less lookups) */
    private static Map<String, String> bySignature;

    private static synchronized void load() {
        if (toSrg != null) return;
        Map<String, String> fwd = new HashMap<>(1 << 18), back = new HashMap<>(1 << 17), sig = new HashMap<>(1 << 17);
        try (InputStream raw = Names.class.getResourceAsStream("/sleepium-names.tsrg.gz")) {
            if (raw == null) throw new IllegalStateException("sleepium-names.tsrg.gz missing from the jar");
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8));
            String owner = null, line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.charAt(0) != '\t') {
                    owner = line.substring(0, line.indexOf(' '));
                    continue;
                }
                String[] p = line.substring(1).split(" ");
                if (p.length == 2) {   // a field: name srg
                    fwd.put(owner + '.' + p[0], p[1]);
                    back.put(p[1], p[0]);
                } else if (p.length == 3 && !p[0].startsWith("<")) {   // a method: name desc srg
                    fwd.put(owner + '.' + p[0] + p[1], p[2]);
                    if (!p[2].equals(p[0])) back.put(p[2], p[0]);
                    String k = p[0] + p[1];
                    String had = sig.putIfAbsent(k, p[2]);
                    if (had != null && !had.equals(p[2])) sig.put(k, "");   // ambiguous without the owner
                }
            }
        } catch (Exception e) {
            System.out.println("[sleep] names table not loaded: " + e);
        }
        bySignature = sig;
        toOfficial = back;
        toSrg = fwd;
    }

    /** The running name of method owner.name(desc) (owner internal, official names). */
    public static String method(String owner, String name, String desc) {
        if (!SRG) return name;
        load();
        String s = toSrg.get(owner + '.' + name + desc);
        if (s == null) s = bySignature.get(name + desc);   // declared in a supertype
        return s == null || s.isEmpty() ? name : s;
    }

    /** The running name of field owner.name (owner internal, official names). */
    public static String field(String owner, String name) {
        if (!SRG) return name;
        load();
        String s = toSrg.get(owner + '.' + name);
        return s == null ? name : s;
    }

    /**
     * The official name of a member name read from running bytecode (unchanged when it is not an SRG name). Also in a
     * dev run: other mods' refmaps name members in SRG there too (MixinTouch).
     */
    public static String official(String running) {
        if (running.length() < 3 || running.charAt(1) != '_' || running.charAt(running.length() - 1) != '_') return running;
        load();
        String o = toOfficial.get(running);
        return o == null ? running : o;
    }
}
