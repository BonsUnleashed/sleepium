package dev.lull.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings screen's catalogue (assets/sleep/options.tsv, made by tools/gen_options.py from defaults.txt and the
 * flags' javadoc): every engine flag with its group, default and description, in groups in a fixed order.
 */
public final class SleepOptions {
    /** the groups, in the order the screen shows them */
    public static final String[] GROUPS = {"chunks", "entities_render", "lighting", "animation", "ticks", "threads", "network", "worldgen", "saving"};

    public record Option(String key, String group, boolean byDefault, String description) {
        /** the flag's short name: lab.opt.ChunkMultiDrawMixin -> ChunkMultiDraw */
        public String shortName() {
            String n = key.substring(key.lastIndexOf('.') + 1);
            if (n.endsWith("Mixin") && n.length() > 5) n = n.substring(0, n.length() - 5);
            return key.startsWith("lab.opt.") ? n : key;
        }
    }

    private static List<Option> all;

    private SleepOptions() {}

    public static synchronized List<Option> all() {
        if (all != null) return all;
        all = new ArrayList<>();
        try (InputStream in = SleepOptions.class.getResourceAsStream("/assets/sleepium/options.tsv")) {
            if (in != null) {
                for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String[] c = line.split("\t", -1);
                    if (c.length >= 4) all.add(new Option(c[0], c[1], "true".equals(c[2]), c[3]));
                }
            }
        } catch (IOException e) {
            System.out.println("[sleep/ui] options: " + e);
        }
        return all;
    }

    public static Map<String, List<Option>> byGroup() {
        Map<String, List<Option>> m = new LinkedHashMap<>();
        for (String g : GROUPS) m.put(g, new ArrayList<>());
        for (Option o : all()) m.computeIfAbsent(o.group(), k -> new ArrayList<>()).add(o);
        return m;
    }
}
