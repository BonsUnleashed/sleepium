package dev.probe.optsupport.sleep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draw call sites (owner.method+desc@line, the analysis' key) by id: handed out at transform time (WriteBarrier's draw
 * sites) and at run time (MobSleep.draw's stack walk). No game classes here: it is loaded while classes are transformed.
 */
public final class DrawSiteIds {
    static final List<String> SITES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<String, Integer> IDS = new HashMap<>();

    private DrawSiteIds() {}

    static synchronized int id(String site) {
        Integer id = IDS.get(site);
        if (id == null) {
            id = SITES.size();
            SITES.add(site);
            IDS.put(site, id);
        }
        return id;
    }
}
