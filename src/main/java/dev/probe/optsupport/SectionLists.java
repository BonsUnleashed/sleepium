package dev.probe.optsupport;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;

/**
 * exp/section-lists (client): the frame's passes over every visible section (three opaque layers, translucent,
 * tripwire, block entities; each a pointer chase through thousands of sections) replaced by compact lists built in one
 * pass, in the same order, and rebuilt only when the visible set or a section's contents changed.
 *
 * <p>The lists hold exactly the sections each vanilla loop would not skip: a layer's sections with an arena entry
 * (ChunkMultiDraw), the translucent / tripwire sections whose compiled layer is not empty, the sections with renderable
 * block entities. A loop over a list therefore does what the loop over all visible sections did. Changes: the visible
 * list compared by references with the last one (no section is read), and a generation bumped by setCompiled,
 * RenderSection.reset and arena stores / releases.
 */
public final class SectionLists {
    private static int gen, builtGen = -1;
    private static Object[] lastRefs = new Object[0];
    private static int lastSize = -1;
    private static ObjectArrayList<SectionRenderDispatcher.RenderSection> built;
    @SuppressWarnings("unchecked")
    private static final ObjectArrayList<SectionRenderDispatcher.RenderSection>[] LAYERS = new ObjectArrayList[8];
    private static final ObjectArrayList<SectionRenderDispatcher.RenderSection> TRANSLUCENT = new ObjectArrayList<>(), TRIPWIRE = new ObjectArrayList<>(),
            BLOCK_ENTITIES = new ObjectArrayList<>();
    public static long builds, reuses;
    /** the layer renderSectionLayer is drawing (set at its head) */
    public static RenderType currentLayer;

    static {
        for (int i = 0; i < LAYERS.length; i++) LAYERS[i] = new ObjectArrayList<>();
    }

    private SectionLists() {}

    public static void changed() {
        gen++;
    }

    private static void refresh(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible) {
        int n = visible.size();
        Object[] refs = visible.elements();
        if (builtGen == gen && built == visible && lastSize == n && java.util.Arrays.equals(refs, 0, n, lastRefs, 0, n)) {
            reuses++;
            return;
        }
        for (var l : LAYERS) l.clear();
        TRANSLUCENT.clear();
        TRIPWIRE.clear();
        BLOCK_ENTITIES.clear();
        RenderType translucent = RenderType.translucent(), tripwire = RenderType.tripwire();
        for (int i = 0; i < n; i++) {
            var s = (SectionRenderDispatcher.RenderSection) refs[i];
            Object[] md = ((ChunkMultiDraw.ChunkEntries) s).probe$md();
            for (int k = 0; k < LAYERS.length; k++) if (md[2 * k] != null || md[2 * k + 1] != null) LAYERS[k].add(s);
            var c = s.getCompiled();
            if (!c.isEmpty(translucent)) TRANSLUCENT.add(s);
            if (!c.isEmpty(tripwire)) TRIPWIRE.add(s);
            if (!c.getRenderableBlockEntities().isEmpty()) BLOCK_ENTITIES.add(s);
        }
        if (lastRefs.length < n) lastRefs = new Object[Math.max(n, lastRefs.length * 2)];
        System.arraycopy(refs, 0, lastRefs, 0, n);
        lastSize = n;
        built = visible;
        builtGen = gen;
        builds++;
    }

    /** ChunkMultiDraw: the visible sections with an arena entry for layer index li. */
    public static ObjectArrayList<SectionRenderDispatcher.RenderSection> layer(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible, int li) {
        refresh(visible);
        return LAYERS[li];
    }

    /** renderSectionLayer's own loops: for translucent and tripwire, only the sections that have the layer. */
    public static ObjectArrayList<SectionRenderDispatcher.RenderSection> forLayer(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible) {
        RenderType t = currentLayer;
        if (t == RenderType.translucent()) {
            refresh(visible);
            return TRANSLUCENT;
        }
        if (t == RenderType.tripwire()) {
            refresh(visible);
            return TRIPWIRE;
        }
        return visible;
    }

    /** renderLevel's block entity loop: only the sections that have renderable block entities. */
    public static ObjectArrayList<SectionRenderDispatcher.RenderSection> blockEntities(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible) {
        refresh(visible);
        return BLOCK_ENTITIES;
    }

    public static String report() {
        return "section lists: built " + builds + ", reused " + reuses;
    }
}
