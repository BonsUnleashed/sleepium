package dev.probe.optsupport;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

/**
 * exp/parallel-tick-scout (server, measurement only, -Dlab.scout=true): would the tick's tasks survive being run in
 * parallel with an in-order commit? A task is one entity tick (with its passengers), one chunk tick (random ticks,
 * precipitation, lightning), one block entity tick, one scheduled block / fluid tick, one block event. Per task the
 * chunk sections it reads (block states, fluids, block entities, entity queries by box) and writes (block states, the
 * section an entity stands in at its start and end), and its global side effects: draws from the level's RNG,
 * entities constructed (the global id counter). At its end, in vanilla order, the task is judged against the writes
 * of the earlier tasks of the same tick:
 * <ul>
 *   <li>all parallel: it read a section some earlier task wrote (it would have to be re-run);</li>
 *   <li>region lanes (1x1, 2x2, 4x4 chunks run sequentially in a lane, lanes in parallel): it read a section an earlier
 *       task of another region wrote.</li>
 * </ul>
 * Coarse on purpose (sections, entity queries as reads only): an upper bound for block conflicts, a lower one for
 * entity-entity effects (a push or a hit on a neighbour is not seen as a write).
 */
public final class ParallelTickScout {
    private ParallelTickScout() {}

    public static final boolean ON = Boolean.getBoolean("lab.scout");
    public static final int ENTITY = 0, CHUNK = 1, BLOCK_ENTITY = 2, SCHEDULED = 3, BLOCK_EVENT = 4;
    private static final String[] KIND = {"entity", "chunk", "block entity", "scheduled tick", "block event"};
    private static final int[] REGION_SHIFT = {0, 1, 2};   // 1x1, 2x2, 4x4 chunks
    private static final long MULTI = Long.MIN_VALUE + 1;

    /** the level being ticked and its RNG (identity) */
    public static Object levelRandom;
    private static int depth = 0;
    private static int kind;
    private static final long[] myRegion = new long[3];
    private static final LongOpenHashSet reads = new LongOpenHashSet(), writes = new LongOpenHashSet();
    /** entity queries and entity moves: a separate kind of data (an entity moving does not change the blocks) */
    private static final LongOpenHashSet entityReads = new LongOpenHashSet(), entityWrites = new LongOpenHashSet();
    private static int rng, spawned;
    /** what the task did beyond its own state: bits of EFFECT_NAMES */
    private static int effects;
    private static long startNanos;
    public static final int SET_BLOCK = 1, ENTITY_ADD_REMOVE = 2, GAME_EVENT = 4, BLOCK_EVENT_Q = 8, PACKETS = 16, LEVEL_RNG = 32, READ_OTHER_CHUNK = 64, READ_OTHER_REGION = 128;
    private static final String[] EFFECT_NAMES = {"setBlock", "entity add/remove", "game event", "block event", "sound/particle/level event", "level RNG", "read other chunk", "read other 4x4 region"};
    // per kind: pure tasks (no effect bit below READ_OTHER_CHUNK), their nanos, all nanos, tasks per effect bit
    private static final long[][] PURE = new long[5][3 + 8];
    private static int myChunkX, myChunkZ;
    /** the task's identity for the draw-count prediction (chunk pos, entity id, block pos) */
    private static long taskKey;
    private static final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap[] LAST_DRAWS = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap[5];
    static { for (int i = 0; i < 5; i++) { LAST_DRAWS[i] = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(); LAST_DRAWS[i].defaultReturnValue(-1); } }
    /** per tick, among the tasks that draw from the level RNG: have all earlier ones been predicted right so far */
    private static boolean chainIntact;
    // per kind: drawing tasks predicted right, drawing tasks after the first wrong prediction of their tick (re-run by the shift)
    private static final long[][] RNG_STATS = new long[5][2];
    private static final LongOpenHashSet written = new LongOpenHashSet(), entityWritten = new LongOpenHashSet();
    private static final Long2LongOpenHashMap[] writer = {new Long2LongOpenHashMap(), new Long2LongOpenHashMap(), new Long2LongOpenHashMap()};
    private static final Long2LongOpenHashMap[] entityWriter = {new Long2LongOpenHashMap(), new Long2LongOpenHashMap(), new Long2LongOpenHashMap()};

    // per kind: tasks, conflicts (all parallel), conflicts per region size, tasks with RNG draws, draws, tasks that spawned, reads, writes
    private static final long[][] STATS = new long[5][10];
    /** the server thread (the only one recorded: chunks are read by the client and worldgen too) */
    private static Thread owner;
    private static long ticks;

    public static void beginTick(Object random) {
        levelRandom = random;
        owner = Thread.currentThread();
        chainIntact = true;
        written.clear();
        entityWritten.clear();
        for (Long2LongOpenHashMap m : writer) m.clear();
        for (Long2LongOpenHashMap m : entityWriter) m.clear();
        if (++ticks % 600 == 0) System.out.println("[probe/scout] " + report());
    }

    private static long region(int cx, int cz, int shift) {
        return (((long) (cx >> shift)) << 32) ^ ((cz >> shift) & 0xFFFFFFFFL);
    }

    public static boolean inTask() {
        return depth > 0;
    }

    public static void begin(int k, int blockX, int blockY, int blockZ) {
        begin(k, blockX, blockY, blockZ, net.minecraft.core.BlockPos.asLong(blockX, blockY, blockZ));
    }

    public static void begin(int k, int blockX, int blockY, int blockZ, long key) {
        if (Thread.currentThread() != owner) return;
        if (depth++ > 0) return;   // nested: part of the outer task
        kind = k;
        taskKey = key;
        int cx = blockX >> 4, cz = blockZ >> 4;
        for (int i = 0; i < 3; i++) myRegion[i] = region(cx, cz, REGION_SHIFT[i]);
        reads.clear();
        writes.clear();
        entityReads.clear();
        entityWrites.clear();
        rng = 0;
        spawned = 0;
        effects = 0;
        myChunkX = blockX >> 4;
        myChunkZ = blockZ >> 4;
        startNanos = System.nanoTime();
    }

    public static void end() {
        if (Thread.currentThread() != owner) return;
        if (--depth > 0) return;
        if (depth < 0) {
            depth = 0;
            return;
        }
        long[] s = STATS[kind];
        s[0]++;
        long dt = System.nanoTime() - startNanos;
        if (rng > 0) effects |= LEVEL_RNG;
        if (spawned > 0) effects |= ENTITY_ADD_REMOVE;
        long[] pu = PURE[kind];
        pu[2] += dt;
        if ((effects & (READ_OTHER_CHUNK - 1)) == 0) {
            pu[0]++;
            pu[1] += dt;
        }
        for (int bit = 0; bit < 8; bit++) if ((effects & (1 << bit)) != 0) pu[3 + bit]++;
        boolean[] lane = new boolean[3];
        boolean full = conflicts(reads, written, writer, lane) | conflicts(entityReads, entityWritten, entityWriter, lane);
        if (full) s[1]++;
        for (int i = 0; i < 3; i++) if (lane[i]) s[2 + i]++;
        if (rng > 0) {
            s[5]++;
            s[6] += rng;
        }
        int last = LAST_DRAWS[kind].put(taskKey, rng);
        if (rng > 0 || last > 0) {
            // a task that draws (now or last tick): its start state needs every earlier drawing task's count
            if (!chainIntact) RNG_STATS[kind][1]++;
            if (last == rng) RNG_STATS[kind][0]++;
            else chainIntact = false;
        }
        if (spawned > 0) s[7]++;
        s[8] += reads.size() + entityReads.size();
        s[9] += writes.size() + entityWrites.size();
        commit(writes, written, writer);
        commit(entityWrites, entityWritten, entityWriter);
    }

    private static boolean conflicts(LongOpenHashSet rs, LongOpenHashSet w, Long2LongOpenHashMap[] by, boolean[] lane) {
        boolean full = false;
        var it = rs.iterator();
        while (it.hasNext()) {
            long sec = it.nextLong();
            if (!full && w.contains(sec)) full = true;
            for (int i = 0; i < 3; i++) {
                if (lane[i]) continue;
                long r = by[i].getOrDefault(sec, Long.MIN_VALUE);
                if (r != Long.MIN_VALUE && (r == MULTI || r != myRegion[i])) lane[i] = true;
            }
        }
        return full;
    }

    private static void commit(LongOpenHashSet ws, LongOpenHashSet w, Long2LongOpenHashMap[] by) {
        var wi = ws.iterator();
        while (wi.hasNext()) {
            long sec = wi.nextLong();
            w.add(sec);
            for (int i = 0; i < 3; i++) {
                long r = by[i].getOrDefault(sec, Long.MIN_VALUE);
                if (r == Long.MIN_VALUE) by[i].put(sec, myRegion[i]);
                else if (r != myRegion[i]) by[i].put(sec, MULTI);
            }
        }
    }

    /** the section an entity stands in (its move changes that section's entity list) */
    public static void entityWrite(int blockX, int blockY, int blockZ) {
        if (depth > 0 && Thread.currentThread() == owner) entityWrites.add(SectionPos.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));
    }

    public static void read(int blockX, int blockY, int blockZ) {
        if (depth > 0 && Thread.currentThread() == owner) {
            reads.add(SectionPos.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));
            int cx = blockX >> 4, cz = blockZ >> 4;
            if (cx != myChunkX || cz != myChunkZ) {
                effects |= READ_OTHER_CHUNK;
                if ((cx >> 2) != (myChunkX >> 2) || (cz >> 2) != (myChunkZ >> 2)) effects |= READ_OTHER_REGION;
            }
        }
    }

    public static void effect(int bit) {
        if (depth > 0 && Thread.currentThread() == owner) effects |= bit;
    }

    public static void write(int blockX, int blockY, int blockZ) {
        if (depth > 0 && Thread.currentThread() == owner) {
            writes.add(SectionPos.asLong(blockX >> 4, blockY >> 4, blockZ >> 4));
            effects |= SET_BLOCK;
        }
    }

    public static void readBox(AABB b) {
        if (depth == 0 || Thread.currentThread() != owner) return;
        int x0 = SectionPos.blockToSectionCoord(b.minX), x1 = SectionPos.blockToSectionCoord(b.maxX);
        int y0 = SectionPos.blockToSectionCoord(b.minY), y1 = SectionPos.blockToSectionCoord(b.maxY);
        int z0 = SectionPos.blockToSectionCoord(b.minZ), z1 = SectionPos.blockToSectionCoord(b.maxZ);
        if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 512) return;   // huge queries: not a task-local read
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) entityReads.add(SectionPos.asLong(x, y, z));
    }

    public static void rngDraw(Object source) {
        if (depth > 0 && source == levelRandom && Thread.currentThread() == owner) rng++;
    }

    public static void entityConstructed() {
        if (depth > 0 && Thread.currentThread() == owner) spawned++;
    }

    public static String report() {
        StringBuilder b = new StringBuilder("parallel tick scout, ").append(ticks).append(" level ticks:");
        for (int k = 0; k < 5; k++) {
            long[] s = STATS[k];
            if (s[0] == 0) continue;
            double n = s[0];
            b.append(String.format(java.util.Locale.ROOT,
                    "%n  %-14s tasks %8d | conflicts: all parallel %5.1f %%, lanes 1x1 %5.1f %%, 2x2 %5.1f %%, 4x4 %5.1f %% | level RNG in %5.1f %% (%.1f draws each) | spawn %4.1f %% | reads %.1f, writes %.2f sections",
                    KIND[k], s[0], 100 * s[1] / n, 100 * s[2] / n, 100 * s[3] / n, 100 * s[4] / n, 100 * s[5] / n, s[5] == 0 ? 0 : (double) s[6] / s[5],
                    100 * s[7] / n, s[8] / n, s[9] / n));
            long[] pu = PURE[k];
            StringBuilder why = new StringBuilder();
            for (int bit = 0; bit < 8; bit++) if (pu[3 + bit] > 0) why.append(String.format(java.util.Locale.ROOT, "%s %.1f %%, ", EFFECT_NAMES[bit], 100.0 * pu[3 + bit] / n));
            b.append(String.format(java.util.Locale.ROOT, "%n  %-14s   pure (own state + reads only): %5.1f %% of tasks, %5.1f %% of their time (%.1f us per task avg) | %s",
                    "", 100.0 * pu[0] / n, pu[2] == 0 ? 0 : 100.0 * pu[1] / pu[2], pu[2] / n / 1000.0, why));
            b.append(String.format(java.util.Locale.ROOT, "%n  %-14s   draw count same as last tick: %5.1f %% of drawing tasks; drawing tasks after a wrong prediction in their tick: %5.1f %% of all tasks",
                    "", 100.0 * RNG_STATS[k][0] / Math.max(1, s[5]), 100.0 * RNG_STATS[k][1] / n));
        }
        return b.toString();
    }
}
