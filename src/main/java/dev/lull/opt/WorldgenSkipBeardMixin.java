package dev.lull.opt;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * WorldgenSkip: Beardifier.compute runs for every block of NOISE and walks every structure piece and jigsaw junction
 * near the chunk (villages: ~34 pieces + ~53 junctions for each of ~98k blocks). Most of them are provably out of
 * reach of a given block: their contribution is exactly 0.0 —
 * <ul>
 *   <li>BEARD_THIN / BEARD_BOX and junctions: outside the 24³ kernel (a coordinate offset outside [-12, 11]);</li>
 *   <li>BURY: distance ≥ 6 (clampedMap → exactly 0.0); ENCAPSULATE: a halved distance ≥ 6; NONE: always.</li>
 * </ul>
 * For each 4×8×4 tile of blocks the pieces that can reach any block of the tile are selected once (conservative
 * interval bounds); compute sums only those, in the original order. The sum starts at +0.0 and can never become −0.0,
 * so leaving out ±0.0 terms changes no bit.
 */
@Mixin(Beardifier.class)
public abstract class WorldgenSkipBeardMixin implements dev.lull.optsupport.CellProof.ZeroOver {
    @Shadow @Final protected ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow @Final protected ObjectListIterator<JigsawJunction> junctionIterator;

    @Shadow
    protected static double getBuryContribution(double x, double y, double z) {
        throw new AssertionError();
    }

    @Shadow
    protected static double getBeardContribution(int x, int y, int z, int height) {
        throw new AssertionError();
    }

    @Unique private Beardifier.Rigid[] sleep$rigids;
    @Unique private JigsawJunction[] sleep$junctions;
    @Unique private int[] sleep$rSel, sleep$jSel;
    @Unique private int sleep$rn, sleep$jn;
    @Unique private int sleep$tx = Integer.MIN_VALUE, sleep$ty, sleep$tz;

    @Unique
    private void sleep$init() {
        java.util.ArrayList<Beardifier.Rigid> r = new java.util.ArrayList<>();
        while (pieceIterator.hasNext()) r.add(pieceIterator.next());
        pieceIterator.back(Integer.MAX_VALUE);
        java.util.ArrayList<JigsawJunction> j = new java.util.ArrayList<>();
        while (junctionIterator.hasNext()) j.add(junctionIterator.next());
        junctionIterator.back(Integer.MAX_VALUE);
        sleep$rigids = r.toArray(new Beardifier.Rigid[0]);
        sleep$junctions = j.toArray(new JigsawJunction[0]);
        sleep$rSel = new int[sleep$rigids.length];
        sleep$jSel = new int[sleep$junctions.length];
    }

    /** distance from [a0, a1] to [b0, b1], 0 if they overlap (the min over the tile of vanilla's max(0, max(min - x, x - max))) */
    @Unique
    private static int sleep$gap(int a0, int a1, int b0, int b1) {
        return Math.max(0, Math.max(b0 - a1, a0 - b1));
    }

    /** true if some integer in [lo, hi] lies in the kernel's offset range [-12, 11] */
    @Unique
    private static boolean sleep$inKernel(int lo, int hi) {
        return hi >= -12 && lo <= 11;
    }

    @Unique
    private static boolean sleep$keepRigid(Beardifier.Rigid rg, int x0, int x1, int y0, int y1, int z0, int z1) {
        BoundingBox b = rg.box();
        int gx = sleep$gap(x0, x1, b.minX(), b.maxX()), gz = sleep$gap(z0, z1, b.minZ(), b.maxZ());
        int k1 = b.minY() + rg.groundLevelDelta();
        TerrainAdjustment ta = rg.terrainAdjustment();
        // BURY uses i2 / 2.0 with i2 = y - k1: zero once |i2| >= 12 for the whole box, or a horizontal distance
        // >= 6 (length >= 6 → clampedMap gives exactly 0.0); ENCAPSULATE: halved distances, zero once one >= 12
        if (ta == TerrainAdjustment.BURY) return gx < 6 && gz < 6 && (y1 - k1) > -12 && (y0 - k1) < 12;
        if (ta == TerrainAdjustment.BEARD_THIN) return gx <= 11 && gz <= 11 && sleep$inKernel(y0 - k1, y1 - k1);
        if (ta == TerrainAdjustment.BEARD_BOX) return gx <= 11 && gz <= 11 && sleep$gap(y0, y1, k1, b.maxY()) <= 11;
        if (ta == TerrainAdjustment.ENCAPSULATE) return gx < 12 && gz < 12 && sleep$gap(y0, y1, b.minY(), b.maxY()) < 12;
        return false;   // NONE contributes 0.0
    }

    @Unique
    private static boolean sleep$keepJunction(JigsawJunction jj, int x0, int x1, int y0, int y1, int z0, int z1) {
        int sx = jj.getSourceX(), sy = jj.getSourceGroundY(), sz = jj.getSourceZ();
        return sleep$inKernel(x0 - sx, x1 - sx) && sleep$inKernel(y0 - sy, y1 - sy) && sleep$inKernel(z0 - sz, z1 - sz);
    }

    @Unique
    private void sleep$select(int x0, int y0, int z0) {
        int x1 = x0 + 3, y1 = y0 + 7, z1 = z0 + 3;
        int n = 0;
        for (int idx = 0; idx < sleep$rigids.length; idx++)
            if (sleep$keepRigid(sleep$rigids[idx], x0, x1, y0, y1, z0, z1)) sleep$rSel[n++] = idx;
        sleep$rn = n;
        n = 0;
        for (int idx = 0; idx < sleep$junctions.length; idx++)
            if (sleep$keepJunction(sleep$junctions[idx], x0, x1, y0, y1, z0, z1)) sleep$jSel[n++] = idx;
        sleep$jn = n;
    }

    @Override
    public boolean sleep$zeroOver(int x0, int y0, int z0, int x1, int y1, int z1) {
        if (sleep$rigids == null) sleep$init();
        for (Beardifier.Rigid r : sleep$rigids) if (sleep$keepRigid(r, x0, x1, y0, y1, z0, z1)) return false;
        for (JigsawJunction j : sleep$junctions) if (sleep$keepJunction(j, x0, x1, y0, y1, z0, z1)) return false;
        return true;
    }

    /**
     * @author probe
     * @reason same sum in the same order, without the terms that are exactly zero for this block's tile
     */
    @Overwrite
    public double compute(DensityFunction.FunctionContext context) {
        if (sleep$rigids == null) sleep$init();
        int i = context.blockX();
        int j = context.blockY();
        int k = context.blockZ();
        int tx = i >> 2, ty = j >> 3, tz = k >> 2;
        if (tx != sleep$tx || ty != sleep$ty || tz != sleep$tz) {
            sleep$select(tx << 2, ty << 3, tz << 2);
            sleep$tx = tx;
            sleep$ty = ty;
            sleep$tz = tz;
        }
        double d0 = 0.0;

        for (int s = 0; s < sleep$rn; s++) {
            Beardifier.Rigid beardifier$rigid = sleep$rigids[sleep$rSel[s]];
            BoundingBox boundingbox = beardifier$rigid.box();
            int l = beardifier$rigid.groundLevelDelta();
            int i1 = Math.max(0, Math.max(boundingbox.minX() - i, i - boundingbox.maxX()));
            int j1 = Math.max(0, Math.max(boundingbox.minZ() - k, k - boundingbox.maxZ()));
            int k1 = boundingbox.minY() + l;
            int l1 = j - k1;

            TerrainAdjustment ta = beardifier$rigid.terrainAdjustment();
            int i2;
            if (ta == TerrainAdjustment.BURY || ta == TerrainAdjustment.BEARD_THIN) i2 = l1;
            else if (ta == TerrainAdjustment.BEARD_BOX) i2 = Math.max(0, Math.max(k1 - j, j - boundingbox.maxY()));
            else if (ta == TerrainAdjustment.ENCAPSULATE) i2 = Math.max(0, Math.max(boundingbox.minY() - j, j - boundingbox.maxY()));
            else i2 = 0;

            if (ta == TerrainAdjustment.BURY) d0 += getBuryContribution((double) i1, (double) i2 / 2.0, (double) j1);
            else if (ta == TerrainAdjustment.BEARD_THIN || ta == TerrainAdjustment.BEARD_BOX) d0 += getBeardContribution(i1, i2, j1, l1) * 0.8;
            else if (ta == TerrainAdjustment.ENCAPSULATE)
                d0 += getBuryContribution((double) i1 / 2.0, (double) i2 / 2.0, (double) j1 / 2.0) * 0.8;
            else d0 += 0.0;   // NONE
        }

        for (int s = 0; s < sleep$jn; s++) {
            JigsawJunction jigsawjunction = sleep$junctions[sleep$jSel[s]];
            int j2 = i - jigsawjunction.getSourceX();
            int k2 = j - jigsawjunction.getSourceGroundY();
            int l2 = k - jigsawjunction.getSourceZ();
            d0 += getBeardContribution(j2, k2, l2, k2) * 0.4;
        }

        return d0;
    }
}
