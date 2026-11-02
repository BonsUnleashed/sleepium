package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A pack's frame as a graph of buffer accesses, made once at load (notes/52 proposal 2): which colour buffers each
 * stage reads and writes, in frame order - begin, prepare, gbuffers (and shadow), deferred, gbuffers again
 * (translucents, hand), composite, final - with the main / alt flips the passes make.
 * <p>
 * Each buffer has two textures; a frame starts with one of them as main (role 0) and the other as alt (role 1), and the
 * flips move the roles between them. A role whose first access in the frame is a full-screen pass writing it (a
 * composite-type pass: no blending, every pixel; no discard in its fragment shader) never shows what the frame-start
 * clear put there, so that clear is skipped: the same pixels, less memory written. Gbuffers programs count as reading
 * and partly writing every buffer they name, at both of their points in the frame (conservative: their draws cover
 * parts of the screen, and which program draws before or after deferred depends on the world).
 * <p>
 * Also reported: passes whose every write is dead (overwritten or cleared before anything reads it).
 */
final class FrameGraph {
    private static final String[] LEGACY = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};
    private static final int NONE = 0, NEEDS = 1, FULL = 2;

    /** clearRole[i][r]: clear buffer i's texture of role r at the frame start */
    final boolean[][] clearRole = new boolean[RenderTargets.COLOR_TEX][2];
    final String report;

    private final int[] role = new int[RenderTargets.COLOR_TEX];     // the role of buffer i's current main texture
    private final int[][] first = new int[RenderTargets.COLOR_TEX][2];
    // liveness: the last write to each role not yet read (the pass that made it), and the passes with a live write
    private final String[][] pendingWrite = new String[RenderTargets.COLOR_TEX][2];
    private final java.util.Map<String, boolean[]> passLive = new java.util.LinkedHashMap<>();

    /** the buffers a source set names (colortexN, legacy aliases, colorimgN) */
    static boolean[] refs(Set<String> ids) {
        boolean[] r = new boolean[RenderTargets.COLOR_TEX];
        for (int i = 0; i < r.length; i++)
            r[i] = ids.contains("colortex" + i) || ids.contains("colorimg" + i) || i < LEGACY.length && ids.contains(LEGACY[i]);
        return r;
    }

    private void read(int i) {
        int r = role[i];
        if (first[i][r] == NONE) first[i][r] = NEEDS;
        String w = pendingWrite[i][r];
        if (w != null) {
            passLive.get(w)[0] = true;
            pendingWrite[i][r] = null;
        }
    }

    /** a write that keeps what is not drawn over: needs the role's previous content */
    private void partialWrite(int i, int r, String pass) {
        if (first[i][r] == NONE) first[i][r] = NEEDS;
        // the earlier content shows through: its writer is live
        String w = pendingWrite[i][r];
        if (w != null) passLive.get(w)[0] = true;
        pendingWrite[i][r] = pass;
    }

    private void fullWrite(int i, int r, String pass) {
        if (first[i][r] == NONE) first[i][r] = FULL;
        pendingWrite[i][r] = pass;   // an earlier unread write to it is dead
    }

    private void gbuffers(List<ProgramSet.Program> programs) {
        String name = "(gbuffers)";
        passLive.putIfAbsent(name, new boolean[] {true});   // draws the scene: not a candidate
        for (ProgramSet.Program p : programs) {
            boolean[] r = refs(PackShader.identifiers(nonNull(p.sources)));
            for (int i = 0; i < r.length; i++) if (r[i]) read(i);
            for (int b : p.drawBuffers) if (b < RenderTargets.COLOR_TEX) partialWrite(b, role[b], name);
        }
        partialWrite(0, role[0], name);   // vanilla draws in the gbuffers framebuffer (colortex0) too
    }

    private void pass(CompositeProgram cp, PackProperties props) {
        ProgramSet.Program p = cp.program;
        String name = p.name;
        passLive.putIfAbsent(name, new boolean[1]);
        List<String> computeSrc = new ArrayList<>(p.computes);
        if (p.sources[3] != null) computeSrc.add(p.sources[3]);
        if (!computeSrc.isEmpty()) {   // (not run yet; when they are, they may touch any buffer they name, anywhere)
            boolean[] r = refs(PackShader.identifiers(computeSrc.toArray(new String[0])));
            for (int i = 0; i < r.length; i++) if (r[i]) partialWrite(i, role[i], name);
        }
        if (cp.id == 0) return;
        Set<String> ids = PackShader.identifiers(nonNull(p.sources));
        boolean[] r = refs(ids);
        for (int i = 0; i < r.length; i++) if (r[i] || cp.wantsMipmap[i]) read(i);
        boolean discard = PackShader.identifiers(nonNull(p.sources[2])).contains("discard");
        for (int b : p.drawBuffers) {
            if (b >= RenderTargets.COLOR_TEX) continue;
            if (discard) partialWrite(b, 1 - role[b], name);
            else fullWrite(b, 1 - role[b], name);
        }
        for (int b : p.drawBuffers) {
            if (b >= RenderTargets.COLOR_TEX) continue;
            String f = props.get("flip." + name + ".colortex" + b);
            if (f == null || f.equals("true")) role[b] = 1 - role[b];
        }
    }

    private static String[] nonNull(String... s) {
        return java.util.Arrays.stream(s).filter(java.util.Objects::nonNull).toArray(String[]::new);
    }

    FrameGraph(ProgramSet set, PackProperties props, List<CompositeProgram> begin, List<CompositeProgram> prepare, List<CompositeProgram> deferred,
            List<CompositeProgram> composite, CompositeProgram finalPass, boolean[] used, boolean[] clear) {
        List<ProgramSet.Program> gb = new ArrayList<>();
        for (ProgramSet.Program p : set.programs.values()) if (ProgramSet.FALLBACK.containsKey(p.name)) gb.add(p);
        for (CompositeProgram cp : begin) pass(cp, props);
        for (CompositeProgram cp : prepare) pass(cp, props);
        gbuffers(gb);
        for (CompositeProgram cp : deferred) pass(cp, props);
        gbuffers(gb);
        for (CompositeProgram cp : composite) pass(cp, props);
        if (finalPass != null && finalPass.id != 0) {
            boolean[] r = refs(PackShader.identifiers(nonNull(finalPass.program.sources)));
            for (int i = 0; i < r.length; i++) if (r[i] || finalPass.wantsMipmap[i]) read(i);
        } else read(0);   // blitted to the window
        // the frame's end: a buffer that is not cleared keeps its content for the next frame (history): live
        for (int i = 0; i < RenderTargets.COLOR_TEX; i++)
            if (used[i] && !clear[i]) for (int k = 0; k < 2; k++) if (pendingWrite[i][k] != null) passLive.get(pendingWrite[i][k])[0] = true;
        StringBuilder sb = new StringBuilder();
        int skipped = 0;
        for (int i = 0; i < RenderTargets.COLOR_TEX; i++) {
            for (int k = 0; k < 2; k++) {
                clearRole[i][k] = used[i] && clear[i] && first[i][k] != FULL;
                if (used[i] && clear[i] && !clearRole[i][k]) {
                    skipped++;
                    sb.append(" colortex").append(i).append(k == 0 ? "/main" : "/alt");
                }
            }
        }
        StringBuilder dead = new StringBuilder();
        for (var e : passLive.entrySet()) if (!e.getValue()[0]) dead.append(' ').append(e.getKey());
        report = "frame graph: " + skipped + " frame-start clears skipped (written whole before any read):" + sb
                + (dead.length() > 0 ? "; passes with no live output:" + dead : "");
    }
}
