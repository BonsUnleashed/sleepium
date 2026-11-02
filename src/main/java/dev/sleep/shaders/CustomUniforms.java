package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A pack's {@code uniform.<type>.<name> = expr} and {@code variable.<type>.<name> = expr} (shaders.properties):
 * parsed once, evaluated on the CPU once per frame in declaration order (later ones may use earlier ones). Values are
 * float[] (1..4 components); booleans are 0 / 1. The OptiFine expression language: + - * / %, comparisons, ! && ||,
 * vector components (.x .y .z .w / .r .g .b .a / .0 ..), functions (if, smooth, in, between, equals, min, max, clamp,
 * abs, sqrt, pow, exp, log, sin ... , vec2/3/4, random). An unknown name (a biome constant this version lacks) is 0.
 */
public final class CustomUniforms {
    interface Node {
        float[] eval(Env env);
    }

    /** the frame's inputs: standard uniform values by name, then custom ones as they are computed */
    public static final class Env {
        final Function<String, float[]> standard;
        final Map<String, float[]> custom = new HashMap<>();
        float dt;

        Env(Function<String, float[]> standard) {
            this.standard = standard;
        }

        float[] get(String name) {
            float[] v = custom.get(name);
            if (v == null) v = standard.apply(name);
            return v == null ? ZERO : v;
        }
    }

    static final float[] ZERO = {0};

    public record Entry(boolean uniform, String type, String name, Node expr) {}

    public final List<Entry> entries = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    /** the values of the last evaluation (uniforms only) */
    public final Map<String, float[]> values = new HashMap<>();
    private final Map<Integer, float[]> smoothState = new HashMap<>();
    private int autoSmoothId = 100000;

    public static CustomUniforms parse(List<String[]> decls) {
        CustomUniforms c = new CustomUniforms();
        for (String[] d : decls) {
            try {
                Parser p = new Parser(d[3], c);
                Node n = p.expr();
                if (p.pos < p.toks.size()) throw new IllegalArgumentException("unexpected " + p.toks.get(p.pos));
                c.entries.add(new Entry(d[0].equals("uniform"), d[1], d[2], n));
            } catch (RuntimeException e) {
                c.errors.add(d[2] + ": " + e.getMessage());
            }
        }
        return c;
    }

    /** once per frame; dt = seconds since the last frame */
    public void evaluate(Function<String, float[]> standard, float dt) {
        Env env = new Env(standard);
        env.dt = dt;
        currentSmooth = smoothState;
        for (Entry e : entries) {
            float[] v = e.expr.eval(env);
            if (e.type.equals("int")) v = new float[] {(float) Math.floor(v[0])};
            else if (e.type.equals("bool")) v = new float[] {v[0] != 0 ? 1 : 0};
            env.custom.put(e.name, v);
            if (e.uniform) values.put(e.name, v);
        }
    }

    private static Map<Integer, float[]> currentSmooth;

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
                l = env -> new float[] {a.eval(env)[0] != 0 || b.eval(env)[0] != 0 ? 1 : 0};
            }
            return l;
        }

        Node and() {
            Node l = cmp();
            while (eat("&&")) {
                Node a = l, b = cmp();
                l = env -> new float[] {a.eval(env)[0] != 0 && b.eval(env)[0] != 0 ? 1 : 0};
            }
            return l;
        }

        Node cmp() {
            Node l = add();
            while (true) {
                String op = peek();
                if (!(op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=") || op.equals("==") || op.equals("!="))) return l;
                pos++;
                Node a = l, b = add();
                l = env -> {
                    float x = a.eval(env)[0], y = b.eval(env)[0];
                    boolean r = switch (op) {
                        case "<" -> x < y;
                        case ">" -> x > y;
                        case "<=" -> x <= y;
                        case ">=" -> x >= y;
                        case "==" -> x == y;
                        default -> x != y;
                    };
                    return new float[] {r ? 1 : 0};
                };
            }
        }

        Node add() {
            Node l = mul();
            while (true) {
                if (eat("+")) {
                    Node a = l, b = mul();
                    l = env -> zip(a.eval(env), b.eval(env), Float::sum);
                } else if (eat("-")) {
                    Node a = l, b = mul();
                    l = env -> zip(a.eval(env), b.eval(env), (x, y) -> x - y);
                } else return l;
            }
        }

        Node mul() {
            Node l = unary();
            while (true) {
                if (eat("*")) {
                    Node a = l, b = unary();
                    l = env -> zip(a.eval(env), b.eval(env), (x, y) -> x * y);
                } else if (eat("/")) {
                    Node a = l, b = unary();
                    l = env -> zip(a.eval(env), b.eval(env), (x, y) -> x / y);
                } else if (eat("%")) {
                    Node a = l, b = unary();
                    l = env -> zip(a.eval(env), b.eval(env), (x, y) -> x % y);
                } else return l;
            }
        }

        Node unary() {
            if (eat("-")) {
                Node a = unary();
                return env -> map(a.eval(env), x -> -x);
            }
            if (eat("!")) {
                Node a = unary();
                return env -> new float[] {a.eval(env)[0] == 0 ? 1 : 0};
            }
            if (eat("+")) return unary();
            return postfix(primary());
        }

        /** .x .y .z .w / .r .g .b .a / .0 .. components */
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
                n = env -> {
                    float[] v = base.eval(env);
                    return new float[] {idx < v.length ? v[idx] : 0};
                };
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
            char c = t.charAt(0);
            if (Character.isDigit(c)) {
                // the tokenizer keeps "1.5" whole; "eyeBrightness.y" arrives as ident "eyeBrightness", ".", "y"? no: '.' then 'y'
                float v = Float.parseFloat(t.replaceAll("[fF]$", ""));
                return env -> new float[] {v};
            }
            if (t.equals("true")) return env -> new float[] {1};
            if (t.equals("false")) return env -> new float[] {0};
            if (t.equals("pi")) return env -> new float[] {(float) Math.PI};
            if (peek().equals("(")) return call(t);
            String name = t;
            return env -> env.get(name);
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
                    return env -> {
                        for (int i = 0; i + 1 < a.length; i += 2) if (a[i].eval(env)[0] != 0) return a[i + 1].eval(env);
                        return a.length % 2 == 1 ? a[a.length - 1].eval(env) : ZERO;
                    };
                case "smooth": {
                    // smooth([id,] value, [fadeUp, [fadeDown]]) - seconds (Complementary's 31536000 = a year: never)
                    final int id;
                    final Node val, up, down;
                    boolean hasId = a.length >= 2 && a.length != 3 || a.length == 4;
                    if (a.length == 4 || a.length == 2) {
                        id = (int) a[0].eval(new Env(n -> null))[0];
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
                    return env -> {
                        float target = val.eval(env)[0];
                        float[] st = currentSmooth.computeIfAbsent(id, k -> new float[] {Float.NaN});
                        if (Float.isNaN(st[0])) {
                            st[0] = target;
                            return new float[] {target};
                        }
                        float fade = target > st[0] ? (up == null ? 1 : up.eval(env)[0]) : (down == null ? (up == null ? 1 : up.eval(env)[0]) : down.eval(env)[0]);
                        float k = fade <= 0 ? 1 : (float) (1 - Math.pow(0.01, env.dt / fade));
                        st[0] += (target - st[0]) * k;
                        return new float[] {st[0]};
                    };
                }
                case "in":
                    return env -> {
                        float x = a[0].eval(env)[0];
                        for (int i = 1; i < a.length; i++) if (a[i].eval(env)[0] == x) return new float[] {1};
                        return new float[] {0};
                    };
                case "between":
                    return env -> {
                        float x = a[0].eval(env)[0];
                        return new float[] {x >= a[1].eval(env)[0] && x <= a[2].eval(env)[0] ? 1 : 0};
                    };
                case "equals":
                    return env -> new float[] {Math.abs(a[0].eval(env)[0] - a[1].eval(env)[0]) <= a[2].eval(env)[0] ? 1 : 0};
                case "min":
                    return env -> {
                        float m = a[0].eval(env)[0];
                        for (int i = 1; i < a.length; i++) m = Math.min(m, a[i].eval(env)[0]);
                        return new float[] {m};
                    };
                case "max":
                    return env -> {
                        float m = a[0].eval(env)[0];
                        for (int i = 1; i < a.length; i++) m = Math.max(m, a[i].eval(env)[0]);
                        return new float[] {m};
                    };
                case "clamp":
                    return env -> new float[] {Math.max(a[1].eval(env)[0], Math.min(a[2].eval(env)[0], a[0].eval(env)[0]))};
                case "mix", "lerp":
                    return env -> {
                        float x = a[0].eval(env)[0], y = a[1].eval(env)[0], k = a[2].eval(env)[0];
                        return new float[] {f.equals("mix") ? x + (y - x) * k : y + (a[2].eval(env)[0] - y) * x};
                    };
                case "edge", "step":
                    return env -> new float[] {a[1].eval(env)[0] < a[0].eval(env)[0] ? 0 : 1};
                case "fmod":
                    return env -> {
                        float x = a[0].eval(env)[0], y = a[1].eval(env)[0];
                        return new float[] {(float) (x - y * Math.floor(x / y))};
                    };
                case "atan2":
                    return env -> new float[] {(float) Math.atan2(a[0].eval(env)[0], a[1].eval(env)[0])};
                case "pow":
                    return env -> new float[] {(float) Math.pow(a[0].eval(env)[0], a[1].eval(env)[0])};
                case "vec2", "vec3", "vec4":
                    return env -> {
                        float[] v = new float[f.charAt(3) - '0'];
                        for (int i = 0; i < v.length && i < a.length; i++) v[i] = a[i].eval(env)[0];
                        return v;
                    };
                case "random":
                    return env -> {
                        double r = Math.random();
                        if (a.length == 2) {
                            float lo = a[0].eval(env)[0], hi = a[1].eval(env)[0];
                            r = lo + r * (hi - lo);
                        }
                        return new float[] {(float) r};
                    };
                default: {
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
                    if (f.equals("atan") && a.length == 2) return env -> new float[] {(float) Math.atan2(a[0].eval(env)[0], a[1].eval(env)[0])};
                    if (f.equals("log") && a.length == 2)
                        return env -> new float[] {(float) (Math.log(a[1].eval(env)[0]) / Math.log(a[0].eval(env)[0]))};
                    return env -> map(a[0].eval(env), x -> (float) op.applyAsDouble(x));
                }
            }
        }
    }

    static float[] zip(float[] a, float[] b, java.util.function.BinaryOperator<Float> f) {
        int n = Math.max(a.length, b.length);
        float[] r = new float[n];
        for (int i = 0; i < n; i++) r[i] = f.apply(a[Math.min(i, a.length - 1)], b[Math.min(i, b.length - 1)]);
        return r;
    }

    static float[] map(float[] a, java.util.function.UnaryOperator<Float> f) {
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) r[i] = f.apply(a[i]);
        return r;
    }
}
