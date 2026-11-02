package dev.lull.optsupport;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * WorldgenSkip (column ceiling), measurement stage: an upper bound of an interpolated function's inner tree over a
 * column (fixed corner x, z; a y range), without computing corners. y-independent subtrees (FlatCache, Cache2D over
 * y-independent functions, 2D noises, splines of those, arithmetic of those) are evaluated exactly at the column —
 * vanilla evaluates them to the same value at any y; y gradients are bounded over the range; every other node by its
 * declared [minValue, maxValue]. Used to find, per corner column, the lowest row above which the function is provably
 * negative.
 */
public final class ColumnBound {
    public static final LongAdder COLUMNS = new LongAdder(), ROWS = new LongAdder(), ROWS_NEGATIVE = new LongAdder(), EVALS = new LongAdder();

    private abstract static class Node {
        double lo, hi;

        abstract void eval(ColumnBound b);
    }

    // column being evaluated
    int x, z, y0, y1;
    long column = Long.MIN_VALUE;

    private static final class Flat extends Node {
        final DensityFunction f;
        long at = Long.MIN_VALUE;

        Flat(DensityFunction f) {
            this.f = f;
        }

        @Override
        void eval(ColumnBound b) {
            if (at != b.column) {
                at = b.column;
                double v = f.compute(new DensityFunction.SinglePointContext(b.x, 0, b.z));
                double m = 1e-12 * Math.abs(v);
                lo = v - m;
                hi = v + m;
            }
        }
    }

    private static final class Bounds extends Node {
        Bounds(double l, double h) {
            lo = l;
            hi = h;
        }

        @Override
        void eval(ColumnBound b) {}
    }

    private static final class Bin extends Node {
        final int op;
        final Node a, c;

        Bin(int op, Node a, Node c) {
            this.op = op;
            this.a = a;
            this.c = c;
        }

        @Override
        void eval(ColumnBound b) {
            a.eval(b);
            c.eval(b);
            switch (op) {
                case 0 -> {
                    lo = a.lo + c.lo;
                    hi = a.hi + c.hi;
                }
                case 1 -> {
                    if (a.lo == 0.0 && a.hi == 0.0) {
                        lo = hi = 0.0;
                    } else {
                        double p1 = a.lo * c.lo, p2 = a.lo * c.hi, p3 = a.hi * c.lo, p4 = a.hi * c.hi;
                        lo = Math.min(Math.min(p1, p2), Math.min(p3, p4));
                        hi = Math.max(Math.max(p1, p2), Math.max(p3, p4));
                        if (Double.isNaN(lo) || Double.isNaN(hi)) {
                            lo = Double.NEGATIVE_INFINITY;
                            hi = Double.POSITIVE_INFINITY;
                        }
                    }
                }
                case 2 -> {
                    lo = Math.min(a.lo, c.lo);
                    hi = Math.min(a.hi, c.hi);
                }
                default -> {
                    lo = Math.max(a.lo, c.lo);
                    hi = Math.max(a.hi, c.hi);
                }
            }
            double m = 1e-12 * (Math.abs(lo) + Math.abs(hi));
            lo -= m;
            hi += m;
        }
    }

    private static final class Unary extends Node {
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
        void eval(ColumnBound b) {
            in.eval(b);
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
                    double a = l * l, c = h * h;
                    lo = (l <= 0 && h >= 0) ? 0 : Math.min(a, c);
                    hi = Math.max(a, c);
                }
                case 2 -> {
                    lo = l * l * l;
                    hi = h * h * h;
                }
                case 3 -> {
                    lo = l > 0 ? l : l * 0.5;
                    hi = h > 0 ? h : h * 0.5;
                }
                case 4 -> {
                    lo = l > 0 ? l : l * 0.25;
                    hi = h > 0 ? h : h * 0.25;
                }
                case 5 -> {
                    double a = Mth.clamp(l, -1.0, 1.0), c = Mth.clamp(h, -1.0, 1.0);
                    lo = a / 2.0 - a * a * a / 24.0;
                    hi = c / 2.0 - c * c * c / 24.0;
                }
                case 6 -> {
                    double a = l * k, c = h * k;
                    lo = Math.min(a, c);
                    hi = Math.max(a, c);
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
        void eval(ColumnBound b) {
            in.eval(b);
            if (in.lo >= minInc && in.hi < maxExc) {
                whenIn.eval(b);
                lo = whenIn.lo;
                hi = whenIn.hi;
            } else if (in.hi < minInc || in.lo >= maxExc) {
                whenOut.eval(b);
                lo = whenOut.lo;
                hi = whenOut.hi;
            } else {
                whenIn.eval(b);
                whenOut.eval(b);
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
        void eval(ColumnBound b) {
            double a = Mth.clampedMap((double) b.y0, (double) fromY, (double) toY, fromV, toV);
            double c = Mth.clampedMap((double) b.y1, (double) fromY, (double) toY, fromV, toV);
            double m = 1e-12 * (Math.abs(a) + Math.abs(c)) + 1e-15;
            lo = Math.min(a, c) - m;
            hi = Math.max(a, c) + m;
        }
    }

    // ---- building ----

    private static Object get(Object o, String name) {
        return Reflect.get(o, name);
    }

    private static double dbl(Object o, String name) {
        return ((Number) get(o, name)).doubleValue();
    }

    private static String simple(Object f) {
        String cn = f.getClass().getName();
        return cn.startsWith("net.minecraft.world.level.levelgen.") ? cn.substring("net.minecraft.world.level.levelgen.".length()) : cn;
    }

    private final Node root;

    private ColumnBound(Node root) {
        this.root = root;
    }

    /** for an interpolator's inner function (its noiseFiller); null if it cannot be walked */
    public static ColumnBound build(DensityFunction inner) {
        try {
            return new ColumnBound(node(inner, new IdentityHashMap<>(), new IdentityHashMap<>()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** y-independent: vanilla evaluates it to the same value at every y of a column */
    private static boolean flat(Object o, IdentityHashMap<Object, Boolean> memo) {
        Boolean m = memo.get(o);
        if (m != null) return m;
        memo.put(o, Boolean.FALSE);   // cycles: not flat
        boolean r = flat0(o, memo);
        memo.put(o, r);
        return r;
    }

    private static boolean flat0(Object o, IdentityHashMap<Object, Boolean> memo) {
        String s = simple(o);
        switch (s) {
            case "NoiseChunk$FlatCache":
                return true;
            case "NoiseChunk$Cache2D":
                return flat(get(o, "function"), memo);
            case "NoiseChunk$CacheOnce":
                return flat(get(o, "function"), memo);
            case "DensityFunctions$Constant", "DensityFunctions$BlendAlpha", "DensityFunctions$BlendOffset",
                 "DensityFunctions$EndIslandDensityFunction", "DensityFunctions$ShiftA", "DensityFunctions$ShiftB":
                return true;
            case "DensityFunctions$Noise":
                return dbl(o, "yScale") == 0.0;
            case "DensityFunctions$ShiftedNoise":
                return dbl(o, "yScale") == 0.0 && flat(get(o, "shiftX"), memo) && flat(get(o, "shiftY"), memo) && flat(get(o, "shiftZ"), memo);
            case "DensityFunctions$Ap2":
                return flat(get(o, "argument1"), memo) && flat(get(o, "argument2"), memo);
            case "DensityFunctions$MulOrAdd", "DensityFunctions$Mapped", "DensityFunctions$Clamp":
                return flat(get(o, "input"), memo);
            case "DensityFunctions$RangeChoice":
                return flat(get(o, "input"), memo) && flat(get(o, "whenInRange"), memo) && flat(get(o, "whenOutOfRange"), memo);
            case "DensityFunctions$HolderHolder":
                return flat(((Holder<?>) get(o, "function")).value(), memo);
            case "DensityFunctions$Spline":
                return flatSpline(get(o, "spline"), memo);
            default:
                return false;
        }
    }

    private static boolean flatSpline(Object spline, IdentityHashMap<Object, Boolean> memo) {
        String s = spline.getClass().getName();
        if (s.endsWith("CubicSpline$Constant")) return true;
        if (!s.endsWith("CubicSpline$Multipoint")) return false;
        Object coord = get(spline, "coordinate");
        if (!coord.getClass().getName().endsWith("DensityFunctions$Spline$Coordinate")) return false;
        if (!flat(((Holder<?>) get(coord, "function")).value(), memo)) return false;
        for (Object v : (List<?>) get(spline, "values")) if (!flatSpline(v, memo)) return false;
        return true;
    }

    private static Node node(DensityFunction f, IdentityHashMap<Object, Boolean> flatMemo, IdentityHashMap<DensityFunction, Node> memo) {
        Node n = memo.get(f);
        if (n == null) {
            n = make(f, flatMemo, memo);
            memo.put(f, n);
        }
        return n;
    }

    private static Node make(DensityFunction f, IdentityHashMap<Object, Boolean> flatMemo, IdentityHashMap<DensityFunction, Node> memo) {
        if (flat(f, flatMemo)) return new Flat(f);
        String s = simple(f);
        switch (s) {
            case "NoiseChunk$CacheOnce":
                return node((DensityFunction) get(f, "function"), flatMemo, memo);
            case "DensityFunctions$BlendDensity":   // empty Blender: identity
                return node((DensityFunction) get(f, "input"), flatMemo, memo);
            case "DensityFunctions$HolderHolder":
                return node((DensityFunction) ((Holder<?>) get(f, "function")).value(), flatMemo, memo);
            case "DensityFunctions$Ap2": {
                int op = switch (((Enum<?>) get(f, "type")).name()) {
                    case "ADD" -> 0;
                    case "MUL" -> 1;
                    case "MIN" -> 2;
                    case "MAX" -> 3;
                    default -> -1;
                };
                if (op >= 0)
                    return new Bin(op, node((DensityFunction) get(f, "argument1"), flatMemo, memo), node((DensityFunction) get(f, "argument2"), flatMemo, memo));
                break;
            }
            case "DensityFunctions$MulOrAdd": {
                String t = ((Enum<?>) get(f, "specificType")).name();
                Node in = node((DensityFunction) get(f, "input"), flatMemo, memo);
                if (t.equals("MUL")) return new Unary(6, in, dbl(f, "argument"), 0);
                if (t.equals("ADD")) return new Unary(7, in, dbl(f, "argument"), 0);
                break;
            }
            case "DensityFunctions$Mapped": {
                int op = switch (((Enum<?>) get(f, "type")).name()) {
                    case "ABS" -> 0;
                    case "SQUARE" -> 1;
                    case "CUBE" -> 2;
                    case "HALF_NEGATIVE" -> 3;
                    case "QUARTER_NEGATIVE" -> 4;
                    case "SQUEEZE" -> 5;
                    default -> -1;
                };
                if (op >= 0) return new Unary(op, node((DensityFunction) get(f, "input"), flatMemo, memo), 0, 0);
                break;
            }
            case "DensityFunctions$Clamp":
                return new Unary(8, node((DensityFunction) get(f, "input"), flatMemo, memo), dbl(f, "minValue"), dbl(f, "maxValue"));
            case "DensityFunctions$RangeChoice":
                return new Range(node((DensityFunction) get(f, "input"), flatMemo, memo), dbl(f, "minInclusive"), dbl(f, "maxExclusive"),
                        node((DensityFunction) get(f, "whenInRange"), flatMemo, memo), node((DensityFunction) get(f, "whenOutOfRange"), flatMemo, memo));
            case "DensityFunctions$YClampedGradient":
                return new YGrad(((Number) get(f, "fromY")).intValue(), ((Number) get(f, "toY")).intValue(), dbl(f, "fromValue"), dbl(f, "toValue"));
            default:
                break;
        }
        return new Bounds(f.minValue(), f.maxValue());
    }

    /** upper bound of the function over x, z (a corner column) and y in [y0, y1] */
    public double upper(int x, int z, int y0, int y1) {
        long col = ((long) x << 32) | (z & 0xFFFFFFFFL);
        if (col != column) {
            column = col;
            this.x = x;
            this.z = z;
        }
        this.y0 = y0;
        this.y1 = y1;
        EVALS.increment();
        root.eval(this);
        return root.hi;
    }

    public static String report() {
        long c = COLUMNS.sumThenReset(), r = ROWS.sumThenReset(), n = ROWS_NEGATIVE.sumThenReset(), e = EVALS.sumThenReset();
        return String.format(java.util.Locale.ROOT, "column ceiling: %d columns, %.1f %% of corner rows provably negative above the ceiling, %d interval evaluations",
                c, r == 0 ? 0.0 : 100.0 * n / r, e);
    }
}
