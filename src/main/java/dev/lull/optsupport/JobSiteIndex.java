package dev.lull.optsupport;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;

/**
 * lab.opt.JobSiteIndex: how many live brains hold each position as their JOB_SITE memory, so PoiCompetitorScan can
 * skip its walk over every nearby living entity (each one's brain read: ~4 us a call, notes/17 §11) when only the
 * scanning villager holds its job site. A competitor must hold the same JOB_SITE, so with a count of one nobody
 * competes, the walk finds nothing and changes nothing: skipping it is exact.
 * <p>Kept from every write of the memory (Brain.setMemoryInternal: set, erase, expiry, load; clearMemories;
 * copyWithoutBehaviors' direct copy) and released when the brain stops being used (its entity removed, the brain
 * replaced). Keyed by block position only (the dimension ignored): a count can only be too high, which falls back to
 * the vanilla walk. A write off the server thread switches the index off for good.
 * -Dlab.jobsite.verify=true: no skipping; every competitor vanilla finds is checked against the count.
 */
public final class JobSiteIndex {
    public static final boolean VERIFY = Boolean.getBoolean("lab.jobsite.verify");
    private static final Object2IntOpenHashMap<BlockPos> COUNT = new Object2IntOpenHashMap<>();
    private static volatile Thread thread;
    private static volatile boolean off;
    public static long skipped, walked, verified, competitors, mismatches;

    private JobSiteIndex() {}

    /** the brain's JOB_SITE went from old to now (either null); returns what the brain should remember */
    public static BlockPos update(BlockPos old, BlockPos now) {
        if (old == null ? now == null : old.equals(now)) return old;
        if (!onThread()) return now;
        if (old != null && COUNT.addTo(old, -1) <= 1) COUNT.removeInt(old);
        if (now != null) COUNT.addTo(now, 1);
        return now;
    }

    /** whether at most one live brain holds pos as its job site (the caller's own) */
    public static boolean alone(BlockPos pos) {
        if (((skipped + walked + verified) & 0xFFFF) == 0xFFFF) System.out.println("[sleep/opt] " + report());
        if (off || Thread.currentThread() != thread) return false;
        if (VERIFY) {
            verified++;
            return false;
        }
        boolean a = COUNT.getInt(pos) <= 1;
        if (a) skipped++;
        else walked++;
        return a;
    }

    /** verify mode: vanilla found a competitor at pos */
    public static void competitor(BlockPos pos) {
        competitors++;
        if (competitors <= 50) System.out.println("[sleep/jobsite] competitor at " + pos + ", brains holding it " + COUNT.getInt(pos));
        if (!off && Thread.currentThread() == thread && COUNT.getInt(pos) <= 1) {
            mismatches++;
            System.out.println("[sleep/jobsite] MISMATCH: a competitor at " + pos + " the index did not count");
        }
    }

    private static boolean onThread() {
        Thread t = Thread.currentThread();
        if (thread == null) thread = t;
        if (t != thread) {
            if (!t.getName().equals("Server thread") || thread.isAlive()) {
                off = true;
                return false;
            }
            thread = t;   // a new integrated server: the counts of the last one no longer meet any brain
            COUNT.clear();
        }
        return true;
    }

    public static String report() {
        return "job site index: " + (off ? "OFF (a write off the server thread), " : "") + "walks skipped " + skipped + ", walked " + walked
                + (VERIFY ? ", verified scans " + verified + " (competitors found " + competitors + "), MISMATCHES " + mismatches : "") + ", positions " + COUNT.size();
    }
}
