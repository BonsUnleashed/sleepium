package dev.probe.optsupport.sleep;

import java.util.HashMap;
import java.util.Map;

/**
 * The write barrier's field ids (owner as written at the site, name, descriptor), handed out at transform time. No game
 * classes here: it is loaded while classes are being transformed.
 */
public final class WBIds {
    static final boolean VERBOSE = Boolean.getBoolean("lab.wb.verbose");
    private static final Map<String, Integer> IDS = new HashMap<>();
    static String[] owner = new String[256], name = new String[256], desc = new String[256];
    static volatile int count;

    private WBIds() {}

    static synchronized int id(String o, String n, String d) {
        String key = o + '.' + n;
        Integer i = IDS.get(key);
        if (i != null) return i;
        int k = count;
        if (k == owner.length) {
            owner = java.util.Arrays.copyOf(owner, k * 2);
            name = java.util.Arrays.copyOf(name, k * 2);
            desc = java.util.Arrays.copyOf(desc, k * 2);
        }
        owner[k] = o;
        name[k] = n;
        desc[k] = d;
        IDS.put(key, k);
        count = k + 1;
        return k;
    }

    static String label(int id) {
        String o = owner[id];
        return o.substring(o.lastIndexOf('/') + 1) + "." + name[id];
    }
}
