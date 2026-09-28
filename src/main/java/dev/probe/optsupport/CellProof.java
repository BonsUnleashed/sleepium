package dev.probe.optsupport;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

/**
 * WorldgenSkip (cells): an upper/lower bound of a NoiseChunk's final density over one noise cell, from the cell's
 * interpolator corners and every other node's declared range — to prove "every block of this cell is ≤ 0" without
 * evaluating 128 blocks.
 *
 * <p>The tree is the NoiseChunk's own wrapped density function (cacheAllInCell(add(finalDensity, beardifier))), walked
 * once per NoiseChunk by reflection into vanilla's node records. Interpolated nodes are leaves bounded by the hull of
 * their 8 corners of the cell (trilinear interpolation stays inside it; a margin covers rounding); arithmetic nodes
 * (add, mul, min, max, mul/add by constant, abs, square, cube, half/quarter negative, squeeze, clamp, range choice,
 * y gradient, constants) use interval arithmetic; the Beardifier is 0 where no structure piece can reach the cell;
 * every other node — noises, splines, and any mod's node — contributes its declared [minValue, maxValue], the same
 * bounds vanilla itself relies on for its min/max shortcuts. Cache wrappers are transparent for NoiseChunk contexts
 * (CacheOnce, CacheAllInCell) or bounded by their declared range (FlatCache, Cache2D: quantised / order-dependent).
 */
public final class CellProof {
    /** The cell being proved. */
    public static final class Cell {
        public int yIndex, zIndex;
        public int x0, y0, z0, x1, y1, z1;
    }

    /** Implemented by the Beardifier mixin: no piece or junction contributes anything inside the box. */
    public interface ZeroOver {
        boolean probe$zeroOver(int x0, int y0, int z0, int x1, int y1, int z1);
    }

    /** Implemented by the aquifer mixin: computeSubstance(block, substance ≤ 0) returns exactly Blocks.AIR for every block. */
    public interface AirBox {
        boolean probe$boxAir(int x0, int y0, int z0, int x1, int y1, int z1);
    }

    /** Implemented by the NoiseChunk mixin. */
    public interface Host {
        @org.jetbrains.annotations.Nullable
        CellProof probe$proof();

        /**
         * After probe$proof: every block of a cell whose density is > 0 gets the default block — the ore filler (if
         * any) provably returns null there. False if the filler list is not vanilla's.
         */
        boolean probe$solidIsDefault(Cell c);
    }

    /** Implemented by the aquifer mixin: leave the state computeSubstance(substance > 0) would have left. */
    public interface Solid {
        void probe$solid();
    }

    /** Implemented by the NoiseInterpolator accessor. */
    public interface Corners {
        double[][] probe$slice0();

        double[][] probe$slice1();
    }

    private abstract static class Node {
        double lo, hi;

        abstract void eval(Cell c);
    }

    private static final class Const extends Node {
        Const(double v) {
            lo = hi = v;
        }

        @Override
        void eval(Cell c) {}
    }

    private static final class Interp extends Node {
        final Corners corners;

        Interp(Corners corners) {
            this.corners = corners;
        }

        @Override
        void eval(Cell c) {
            double[][] s0 = corners.probe$slice0(), s1 = corners.probe$slice1();
            int y = c.yIndex, z = c.zIndex;
            double a = s0[z][y], b = s0[z + 1][y], d = s1[z][y], e = s1[z + 1][y];
            double f = s0[z][y + 1], g = s0[z + 1][y + 1], h = s1[z][y + 1], k = s1[z + 1][y + 1];
            double mn = Math.min(Math.min(Math.min(a, b), Math.min(d, e)), Math.min(Math.min(f, g), Math.min(h, k)));
            double mx = Math.max(Math.max(Math.max(a, b), Math.max(d, e)), Math.max(Math.max(f, g), Math.max(h, k)));
            double m = 1e-9 * (Math.abs(mn) + Math.abs(mx) + 1.0);   // lerp rounding, far above any real error
            lo = mn - m;
            hi = mx + m;
        }
    }

    private static final class Bin extends Node {
        final int op;   // 0 add, 1 mul, 2 min, 3 max
        final Node a, b;

        Bin(int op, Node a, Node b) {
            this.op = op;
            this.a = a;
            this.b = b;
        }

        @Override
        void eval(Cell c) {
            a.eval(c);
            b.eval(c);
            switch (op) {
                case 0 -> {
                    lo = a.lo + b.lo;
                    hi = a.hi + b.hi;
                }
                case 1 -> mul(a.lo, a.hi, b.lo, b.hi);
                case 2 -> {
                    lo = Math.min(a.lo, b.lo);
                    hi = Math.min(a.hi, b.hi);
                }
                default -> {
                    lo = Math.max(a.lo, b.lo);
                    hi = Math.max(a.hi, b.hi);
                }
            }
            widen();
        }

        private void mul(double al, double ah, double bl, double bh) {
            if (al == 0.0 && ah == 0.0) {   // vanilla: d0 == 0 ? 0 : d0 * arg2
                lo = hi = 0.0;
                return;
            }
            double p1 = al * bl, p2 = al * bh, p3 = ah * bl, p4 = ah * bh;
            lo = Math.min(Math.min(p1, p2), Math.min(p3, p4));
            hi = Math.max(Math.max(p1, p2), Math.max(p3, p4));
            if (Double.isNaN(lo) || Double.isNaN(hi)) {   // 0 × ∞
                lo = Double.NEGATIVE_INFINITY;
                hi = Double.POSITIVE_INFINITY;
            }
        }

        private void widen() {
            double m = 1e-12 * (Math.abs(lo) + Math.abs(hi));
            lo -= m;
            hi += m;
        }
    }

    private static final class Unary extends Node {
        /** 0 abs, 1 square, 2 cube, 3 half negative, 4 quarter negative, 5 squeeze, 6 mul k, 7 add k, 8 clamp */
        final int op;
        final Node in;
        final double k, k2;

        Unary(int op, Node in, double k, double k2) {
            this.op = op;
            this.in = in;
            this.k = k;
            this.k2 = k2;
        }

        @Override
        void eval(Cell c) {
            in.eval(c);
            double l = in.lo, h = in.hi;
            switch (op) {
                case 0 -> {
                    if (l >= 0) {
                        lo = l;
                        hi = h;
                    } else if (h <= 0) {
                        lo = -h;
                        hi = -l;
                    } else {
                        lo = 0;
                        hi = Math.max(-l, h);
                    }
                }
                case 1 -> {
                    double a = l * l, b = h * h;
                    lo = (l <= 0 && h >= 0) ? 0 : Math.min(a, b);
                    hi = Math.max(a, b);
                }
                case 2 -> {   // monotone
                    lo = l * l * l;
                    hi = h * h * h;
                }
                case 3 -> {   // monotone: x > 0 ? x : x * 0.5
                    lo = l > 0 ? l : l * 0.5;
                    hi = h > 0 ? h : h * 0.5;
                }
                case 4 -> {
                    lo = l > 0 ? l : l * 0.25;
                    hi = h > 0 ? h : h * 0.25;
                }
                case 5 -> {   // clamp to [-1, 1], then d/2 - d³/24: increasing on [-1, 1]
                    double a = Mth.clamp(l, -1.0, 1.0), b = Mth.clamp(h, -1.0, 1.0);
                    lo = a / 2.0 - a * a * a / 24.0;
                    hi = b / 2.0 - b * b * b / 24.0;
                }
                case 6 -> {
                    double a = l * k, b = h * k;
                    lo = Math.min(a, b);
                    hi = Math.max(a, b);
                    if (Double.isNaN(lo) || Double.isNaN(hi)) {
                        lo = Double.NEGATIVE_INFINITY;
                        hi = Double.POSITIVE_INFINITY;
                    }
                }
                case 7 -> {
                    lo = l + k;
                    hi = h + k;
                }
                default -> {
                    lo = Mth.clamp(l, k, k2);
                    hi = Mth.clamp(h, k, k2);
                }
            }
            double m = 1e-12 * (Math.abs(lo) + Math.abs(hi));
            lo -= m;
            hi += m;
        }
    }

    private static final class Range extends Node {
        final Node in, whenIn, whenOut;
        final double minInc, maxExc;

        Range(Node in, double minInc, double maxExc, Node whenIn, Node whenOut) {
            this.in = in;
            this.minInc = minInc;
            this.maxExc = maxExc;
            this.whenIn = whenIn;
            this.whenOut = whenOut;
        }

        @Override
        void eval(Cell c) {
            in.eval(c);
            boolean allIn = in.lo >= minInc && in.hi < maxExc;
            boolean allOut = in.hi < minInc || in.lo >= maxExc;
            if (allIn) {
                whenIn.eval(c);
                lo = whenIn.lo;
                hi = whenIn.hi;
            } else if (allOut) {
                whenOut.eval(c);
                lo = whenOut.lo;
                hi = whenOut.hi;
            } else {
                whenIn.eval(c);
                whenOut.eval(c);
                lo = Math.min(whenIn.lo, whenOut.lo);
                hi = Math.max(whenIn.hi, whenOut.hi);
            }
        }
    }

    private static final class YGrad extends Node {
        final int fromY, toY;
        final double fromV, toV;

        YGrad(int fromY, int toY, double fromV, double toV) {
            this.fromY = fromY;
            this.toY = toY;
            this.fromV = fromV;
            this.toV = toV;
        }

        @Override
        void eval(Cell c) {   // clampedMap(y, fromY, toY, fromV, toV): monotone in y
            double a = Mth.clampedMap((double) c.y0, (double) fromY, (double) toY, fromV, toV);
            double b = Mth.clampedMap((double) c.y1, (double) fromY, (double) toY, fromV, toV);
            double m = 1e-12 * (Math.abs(a) + Math.abs(b)) + 1e-15;
            lo = Math.min(a, b) - m;
            hi = Math.max(a, b) + m;
        }
    }

    private static final class Beard extends Node {
        final ZeroOver b;

        Beard(ZeroOver b) {
            this.b = b;
        }

        @Override
        void eval(Cell c) {
            if (b.probe$zeroOver(c.x0, c.y0, c.z0, c.x1, c.y1, c.z1)) {
                lo = hi = 0.0;
            } else {
                lo = Double.NEGATIVE_INFINITY;
                hi = Double.POSITIVE_INFINITY;
            }
        }
    }

    private static final class Bounds extends Node {
        Bounds(double l, double h) {
            lo = l;
            hi = h;
        }

        @Override
        void eval(Cell c) {}
    }

    // ---- building ----

    private static Object get(Object o, String name) {
        return Reflect.get(o, name);
    }

    private static double dbl(Object o, String name) {
        return ((Number) get(o, name)).doubleValue();
    }

    private final Node root;
    public final int nodes;

    private CellProof(Node root, int nodes) {
        this.root = root;
        this.nodes = nodes;
    }

    /** null if the tree cannot be walked (then the chunk is generated the vanilla way) */
    public static CellProof build(DensityFunction f) {
        try {
            int[] count = {0};
            return new CellProof(node(f, new IdentityHashMap<>(), count), count[0]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Node node(DensityFunction f, IdentityHashMap<DensityFunction, Node> memo, int[] count) {
        Node n = memo.get(f);
        if (n == null) {
            n = make(f, memo, count);
            memo.put(f, n);
            count[0]++;
        }
        return n;
    }

    private static Node make(DensityFunction f, IdentityHashMap<DensityFunction, Node> memo, int[] count) {
        String cn = f.getClass().getName();
        if (f instanceof Corners c) return new Interp(c);
        if (f instanceof ZeroOver z) return new Beard(z);
        String s = cn.startsWith("net.minecraft.world.level.levelgen.") ? cn.substring("net.minecraft.world.level.levelgen.".length()) : cn;
        switch (s) {
            case "NoiseChunk$CacheAllInCell":
                return node((DensityFunction) get(f, "noiseFiller"), memo, count);
            case "NoiseChunk$CacheOnce":
                return node((DensityFunction) get(f, "function"), memo, count);
            case "DensityFunctions$BlendDensity":   // only built with an empty Blender: identity
                return node((DensityFunction) get(f, "input"), memo, count);
            case "DensityFunctions$HolderHolder":
                return node((DensityFunction) ((Holder<?>) get(f, "function")).value(), memo, count);
            case "DensityFunctions$Marker":
                return node((DensityFunction) get(f, "wrapped"), memo, count);
            case "DensityFunctions$Constant":
                return new Const(dbl(f, "value"));
            case "DensityFunctions$BlendAlpha":
                return new Const(1.0);
            case "DensityFunctions$BlendOffset", "DensityFunctions$BeardifierMarker":
                return new Const(0.0);
            case "DensityFunctions$Ap2": {
                String t = ((Enum<?>) get(f, "type")).name();
                int op = switch (t) {
                    case "ADD" -> 0;
                    case "MUL" -> 1;
                    case "MIN" -> 2;
                    case "MAX" -> 3;
                    default -> -1;
                };
                if (op < 0) break;
                return new Bin(op, node((DensityFunction) get(f, "argument1"), memo, count), node((DensityFunction) get(f, "argument2"), memo, count));
            }
            case "DensityFunctions$MulOrAdd": {
                String t = ((Enum<?>) get(f, "specificType")).name();
                Node in = node((DensityFunction) get(f, "input"), memo, count);
                double a = dbl(f, "argument");
                if (t.equals("MUL")) return new Unary(6, in, a, 0);
                if (t.equals("ADD")) return new Unary(7, in, a, 0);
                break;
            }
            case "DensityFunctions$Mapped": {
                String t = ((Enum<?>) get(f, "type")).name();
                Node in = node((DensityFunction) get(f, "input"), memo, count);
                int op = switch (t) {
                    case "ABS" -> 0;
                    case "SQUARE" -> 1;
                    case "CUBE" -> 2;
                    case "HALF_NEGATIVE" -> 3;
                    case "QUARTER_NEGATIVE" -> 4;
                    case "SQUEEZE" -> 5;
                    default -> -1;
                };
                if (op < 0) break;
                return new Unary(op, in, 0, 0);
            }
            case "DensityFunctions$Clamp":
                return new Unary(8, node((DensityFunction) get(f, "input"), memo, count), dbl(f, "minValue"), dbl(f, "maxValue"));
            case "DensityFunctions$RangeChoice":
                return new Range(node((DensityFunction) get(f, "input"), memo, count), dbl(f, "minInclusive"), dbl(f, "maxExclusive"),
                        node((DensityFunction) get(f, "whenInRange"), memo, count), node((DensityFunction) get(f, "whenOutOfRange"), memo, count));
            case "DensityFunctions$YClampedGradient":
                return new YGrad(((Number) get(f, "fromY")).intValue(), ((Number) get(f, "toY")).intValue(), dbl(f, "fromValue"), dbl(f, "toValue"));
            default:
                break;
        }
        // noises, splines, FlatCache / Cache2D, any mod's node: its declared range
        return new Bounds(f.minValue(), f.maxValue());
    }

    /** true if every block of the cell has density strictly below zero (substance ≤ 0 → never "solid") */
    public boolean allNegative(Cell c) {
        root.eval(c);
        return root.hi < -1e-9;
    }

    /** after allNegative: true if every block of the cell has density strictly above zero (the aquifer returns null) */
    public boolean lastAllPositive() {
        return root.lo > 1e-9;
    }

    /** after an eval: the largest |value| over the cell */
    public double lastMaxAbs() {
        return Math.max(Math.abs(root.lo), Math.abs(root.hi));
    }

    @SuppressWarnings("unused")
    private static boolean isNoiseChunkNode(Object o) {
        return o.getClass().getNestHost() == NoiseChunk.class;
    }
}
