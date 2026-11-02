package dev.lull.shaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A pack's {@code uniform.<type>.<name> = expr} and {@code variable.<type>.<name> = expr} (shaders.properties):
 * parsed once, evaluated on the CPU once per frame in declaration order (later ones may use earlier ones). The
 * OptiFine expression language: + - * / %, comparisons, ! && ||, vector components (.x .y .z .w / .r .g .b .a / .0 ..),
 * functions (if, smooth, in, between, equals, min, max, clamp, abs, sqrt, pow, exp, log, sin ..., vec2/3/4, random).
 * An unknown name (a biome constant this version lacks) is 0.
 *
 * <p>No allocation per frame: every node evaluates one component (scalars broadcast), each result is written into its
 * entry's fixed slot, and every name is resolved once into a reference to a fixed slot (a standard uniform's, an
 * earlier entry's, a constant) - the frame's data stays in the same few cache lines.
 */
public final class CustomUniforms {
    interface Node {
        /** component c (0..3) of the value */
        float eval(int c);
    }

    public record Entry(boolean uniform, String type, String name, Node expr, float[] slot, int size) {}

    public final List<Entry> entries = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    /** uniform name -> its slot (filled by evaluate) */
    public final Map<String, float[]> values = new HashMap<>();
    /** every entry (uniform or variable) by name, for later expressions */
    private final Map<String, float[]> slots = new HashMap<>();
    private final List<Var> vars = new ArrayList<>();
    private final List<float[]> smooths = new ArrayList<>();
    private Function<String, float[]> standard;
    private int autoSmoothId = 100000;
    private final Map<Integer, float[]> smoothById = new HashMap<>();
    private float dt;

    public static CustomUniforms parse(List<String[]> decls) {
        CustomUniforms c = new CustomUniforms();
        for (String[] d : decls) {
            int size = switch (d[1]) {
                case "vec2" -> 2;
                case "vec3" -> 3;
                case "vec4" -> 4;
                default -> 1;
            };
            float[] slot = new float[16];
            try {
                Parser p = new Parser(d[3], c);
                Node n = p.expr();
                if (p.pos < p.toks.size()) throw new IllegalArgumentException("unexpected " + p.toks.get(p.pos));
                c.entries.add(new Entry(d[0].equals("uniform"), d[1], d[2], n, slot, size));
                c.slots.put(d[2], slot);
                if (d[0].equals("uniform")) c.values.put(d[2], slot);
            } catch (RuntimeException e) {
                c.errors.add(d[2] + ": " + e.getMessage());
            }
        }
        return c;
    }

    /** once per frame; standard: a name's slot (stable across frames); dt = seconds since the last frame */
    public void evaluate(Function<String, float[]> standard, float dt) {
        this.standard = standard;
        this.dt = dt;
        for (Entry e : entries) {
            for (int c = 0; c < e.size; c++) e.slot[c] = e.expr.eval(c);
            if (e.type.equals("int")) e.slot[0] = (float) Math.floor(e.slot[0]);
            else if (e.type.equals("bool")) e.slot[0] = e.slot[0] != 0 ? 1 : 0;
        }
    }

    static final float[] ZERO = new float[16];

    /** a name: resolved once (earlier entry, standard uniform slot, else the constant 0) */
    final class Var implements Node {
        final String name;
        float[] ref;

        Var(String name) {
            this.name = name;
        }

        @Override
        public float eval(int c) {
            float[] r = ref;
            if (r == null) {
                r = slots.get(name);
                if (r == null && standard != null) r = standard.apply(name);
                ref = r = r == null ? ZERO : r;
            }
            return r[c];
        }
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        final List<String> toks;
        final CustomUniforms owner;
        int pos;

        Parser(String s, CustomUniforms owner) {
            this.toks = GlslPreprocessor.tokenize(s);
            this.owner = owner;
        }

        String peek() {
            return pos < toks.size() ? toks.get(pos) : "";
        }

        boolean eat(String s) {
            if (peek().equals(s)) {
                pos++;
                return true;
            }
            return false;
        }

        Node expr() {
            return or();
        }

        Node or() {
            Node l = and();
            while (eat("||")) {
                Node a = l, b = and();
                l = c -> a.eval(c) != 0 || b.eval(c) != 0 ? 1 : 0;
            }
            return l;
        }

        Node and() {
            Node l = cmp();
            while (eat("&&")) {
                Node a = l, b = cmp();
                l = c -> a.eval(c) != 0 && b.eval(c) != 0 ? 1 : 0;
            }
            return l;
        }

        Node cmp() {
            Node l = add();
            while (true) {
                String op = peek();
                Node a = l;
                switch (op) {
                    case "<" -> {
                        pos++;
                        Node b = add();
                        l = c -> a.eval(c) < b.eval(c) ? 1 : 0;
                    }
                    case ">" -> {
                        pos++;
                        Node b = add();
                        l = c -> a.eval(c) > b.eval(c) ? 1 : 0;
                    }
                    case "<=" -> {
                        pos++;
                        Node b = add();
                        l = c -> a.eval(c) <= b.eval(c) ? 1 : 0;
                    }
                    case ">=" -> {
                        pos++;
                        Node b = add();
                        l = c -> a.eval(c) >= b.eval(c) ? 1 : 0;
                    }
                    case "==" -> {
                        pos++;
                        Node b = add();
                        l = c -> a.eval(c) == b.eval(c) ? 1 : 0;
                    }
                    case "!=" -> {
                        pos++;
                        Node b = add();
                        l = c -> a.eval(c) != b.eval(c) ? 1 : 0;
                    }
                    default -> {
                        return l;
                    }
                }
            }
        }

        Node add() {
            Node l = mul();
            while (true) {
                Node a = l;
                if (eat("+")) {
                    Node b = mul();
                    l = c -> a.eval(c) + b.eval(c);
                } else if (eat("-")) {
                    Node b = mul();
                    l = c -> a.eval(c) - b.eval(c);
                } else return l;
            }
        }

        Node mul() {
            Node l = unary();
            while (true) {
                Node a = l;
                if (eat("*")) {
                    Node b = unary();
                    l = c -> a.eval(c) * b.eval(c);
                } else if (eat("/")) {
                    Node b = unary();
                    l = c -> a.eval(c) / b.eval(c);
                } else if (eat("%")) {
                    Node b = unary();
                    l = c -> a.eval(c) % b.eval(c);
                } else return l;
            }
        }

        Node unary() {
            if (eat("-")) {
                Node a = unary();
                return c -> -a.eval(c);
            }
            if (eat("!")) {
                Node a = unary();
                return c -> a.eval(c) == 0 ? 1 : 0;
            }
            if (eat("+")) return unary();
            return postfix(primary());
        }

        /** .x .y .z .w / .r .g .b .a / .0 .. components (a scalar result: every component reads that one) */
        Node postfix(Node n) {
            while (peek().equals(".") || peek().startsWith(".") && peek().length() > 1) {
                String t = toks.get(pos++);
                String comp = t.equals(".") ? toks.get(pos++) : t.substring(1);
                int idx = switch (comp) {
                    case "x", "r", "s", "0" -> 0;
                    case "y", "g", "t", "1" -> 1;
                    case "z", "b", "p", "2" -> 2;
                    case "w", "a", "q", "3" -> 3;
                    default -> throw new IllegalArgumentException("component " + comp);
                };
                Node base = n;
                n = c -> base.eval(idx);
            }
            return n;
        }

        Node primary() {
            if (eat("(")) {
                Node n = expr();
                if (!eat(")")) throw new IllegalArgumentException("')' expected");
                return n;
            }
            String t = peek();
            if (t.isEmpty()) throw new IllegalArgumentException("value expected");
            pos++;
            char ch = t.charAt(0);
            if (Character.isDigit(ch) || ch == '.') {
                float v = Float.parseFloat(t.replaceAll("[fF]$", ""));
                return c -> v;
            }
            if (t.equals("true")) return c -> 1;
            if (t.equals("false")) return c -> 0;
            if (t.equals("pi")) return c -> (float) Math.PI;
            if (peek().equals("(")) return call(t);
            return owner.new Var(t);
        }

        Node call(String f) {
            pos++;   // (
            List<Node> args = new ArrayList<>();
            if (!eat(")")) {
                do args.add(expr());
                while (eat(","));
                if (!eat(")")) throw new IllegalArgumentException("')' expected after " + f);
            }
            Node[] a = args.toArray(new Node[0]);
            switch (f) {
                case "if":
                    return c -> {
                        for (int i = 0; i + 1 < a.length; i += 2) if (a[i].eval(0) != 0) return a[i + 1].eval(c);
                        return a.length % 2 == 1 ? a[a.length - 1].eval(c) : 0;
                    };
                case "smooth": {
                    // smooth([id,] value, [fadeUp, [fadeDown]]) - seconds (Complementary's 31536000 = a year: never)
                    final int id;
                    final Node val, up, down;
                    if (a.length == 4 || a.length == 2) {
                        id = (int) a[0].eval(0);
                        val = a[1];
                        up = a.length == 4 ? a[2] : null;
                        down = a.length == 4 ? a[3] : null;
                    } else if (a.length == 3) {
                        id = owner.autoSmoothId++;
                        val = a[0];
                        up = a[1];
                        down = a[2];
                    } else {
                        id = owner.autoSmoothId++;
                        val = a[0];
                        up = null;
                        down = null;
                    }
                    float[] st = owner.smoothById.computeIfAbsent(id, k -> new float[] {Float.NaN});
                    return c -> {
                        float target = val.eval(0);
                        if (Float.isNaN(st[0])) return st[0] = target;
                        float fade = target > st[0] ? (up == null ? 1 : up.eval(0)) : (down == null ? (up == null ? 1 : up.eval(0)) : down.eval(0));
                        float k = fade <= 0 ? 1 : (float) (1 - Math.pow(0.01, owner.dt / fade));
                        return st[0] += (target - st[0]) * k;
                    };
                }
                case "in":
                    return c -> {
                        float x = a[0].eval(0);
                        for (int i = 1; i < a.length; i++) if (a[i].eval(0) == x) return 1;
                        return 0;
                    };
                case "between":
                    return c -> {
                        float x = a[0].eval(0);
                        return x >= a[1].eval(0) && x <= a[2].eval(0) ? 1 : 0;
                    };
                case "equals":
                    return c -> Math.abs(a[0].eval(0) - a[1].eval(0)) <= a[2].eval(0) ? 1 : 0;
                case "min":
                    return c -> {
                        float m = a[0].eval(c);
                        for (int i = 1; i < a.length; i++) m = Math.min(m, a[i].eval(c));
                        return m;
                    };
                case "max":
                    return c -> {
                        float m = a[0].eval(c);
                        for (int i = 1; i < a.length; i++) m = Math.max(m, a[i].eval(c));
                        return m;
                    };
                case "clamp":
                    return c -> Math.max(a[1].eval(c), Math.min(a[2].eval(c), a[0].eval(c)));
                case "mix":
                    return c -> {
                        float x = a[0].eval(c);
                        return x + (a[1].eval(c) - x) * a[2].eval(c);
                    };
                case "lerp":   // lerp(k, x, y) (OptiFine)
                    return c -> {
                        float x = a[1].eval(c);
                        return x + (a[2].eval(c) - x) * a[0].eval(c);
                    };
                case "edge", "step":
                    return c -> a[1].eval(c) < a[0].eval(c) ? 0 : 1;
                case "fmod":
                    return c -> {
                        float x = a[0].eval(c), y = a[1].eval(c);
                        return (float) (x - y * Math.floor(x / y));
                    };
                case "atan2":
                    return c -> (float) Math.atan2(a[0].eval(c), a[1].eval(c));
                case "pow":
                    return c -> (float) Math.pow(a[0].eval(c), a[1].eval(c));
                case "vec2", "vec3", "vec4":
                    return c -> c < a.length ? a[c].eval(0) : 0;
                case "random":
                    return c -> {
                        double r = Math.random();
                        if (a.length == 2) {
                            float lo = a[0].eval(0), hi = a[1].eval(0);
                            r = lo + r * (hi - lo);
                        }
                        return (float) r;
                    };
                default: {
                    if (f.equals("atan") && a.length == 2) return c -> (float) Math.atan2(a[0].eval(c), a[1].eval(c));
                    if (f.equals("log") && a.length == 2) return c -> (float) (Math.log(a[1].eval(c)) / Math.log(a[0].eval(c)));
                    java.util.function.DoubleUnaryOperator op = switch (f) {
                        case "sin" -> Math::sin;
                        case "cos" -> Math::cos;
                        case "tan" -> Math::tan;
                        case "asin" -> Math::asin;
                        case "acos" -> Math::acos;
                        case "atan" -> Math::atan;
                        case "torad", "radians" -> Math::toRadians;
                        case "todeg", "degrees" -> Math::toDegrees;
                        case "exp" -> Math::exp;
                        case "exp2" -> x -> Math.pow(2, x);
                        case "exp10" -> x -> Math.pow(10, x);
                        case "log" -> Math::log;
                        case "log2" -> x -> Math.log(x) / Math.log(2);
                        case "log10" -> Math::log10;
                        case "sqrt" -> Math::sqrt;
                        case "abs" -> Math::abs;
                        case "sign", "signum" -> Math::signum;
                        case "floor" -> Math::floor;
                        case "ceil" -> Math::ceil;
                        case "frac" -> x -> x - Math.floor(x);
                        case "round" -> x -> (double) Math.round(x);
                        default -> throw new IllegalArgumentException("function " + f);
                    };
                    Node x = a[0];
                    return c -> (float) op.applyAsDouble(x.eval(c));
                }
            }
        }
    }
}
