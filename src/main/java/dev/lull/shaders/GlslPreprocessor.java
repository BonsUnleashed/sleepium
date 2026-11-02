package dev.lull.shaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The C preprocessor part a shaderpack needs before its GLSL reaches the driver: {@code #include} (relative to the file,
 * or to shaders/ with a leading '/'), the conditionals ({@code #if/#ifdef/#ifndef/#elif/#else/#endif}, evaluated with
 * the macros defined so far, object- and function-like, {@code defined}), standard macros inserted after
 * {@code #version}. Inactive lines are dropped; active {@code #define / #undef} lines stay (the driver expands the code
 * with the same definitions in the same order) and comments stay (DRAWBUFFERS / RENDERTARGETS and the pack's const
 * directives live in block comments and are read from this output: only the active ones count).
 */
public final class GlslPreprocessor {
    /** a macro: params == null for object-like */
    record Macro(String name, List<String> params, String body) {}

    private final Function<String, String> files;
    private final Map<String, Macro> macros = new HashMap<>();
    private final List<String> standard;
    private final StringBuilder out = new StringBuilder();
    private final List<String> errors = new ArrayList<>();
    private boolean versionSeen;
    /** a rewrite of each source line before it is read (option values: the pack's #define lines changed) */
    private final Function<String, String> lineRewrite;

    /**
     * files: path relative to shaders/ -> text (null if absent); standard: "NAME" or "NAME value" lines defined right
     * after #version; lineRewrite: applied to every raw line (may be identity).
     */
    public GlslPreprocessor(Function<String, String> files, List<String> standard, Function<String, String> lineRewrite) {
        this.files = files;
        this.standard = standard;
        this.lineRewrite = lineRewrite;
    }

    public List<String> errors() {
        return errors;
    }

    public boolean defined(String name) {
        return macros.containsKey(name);
    }

    /** the preprocessed text of the file at path */
    public String process(String path) {
        String text = files.apply(path);
        if (text == null) {
            errors.add("missing " + path);
            return "";
        }
        if (!versionSeen) {
            // define the standard macros up front too (a file without #version still sees them)
            for (String s : standard) define(s.trim());
        }
        include(path, text, 0);
        return out.toString();
    }

    // ------------------------------------------------------------------ files and lines

    private final ArrayList<int[]> condStack = new ArrayList<>();   // {active, anyTaken, parentActive}

    private boolean active() {
        return condStack.isEmpty() || condStack.get(condStack.size() - 1)[0] == 1;
    }

    private void include(String path, String text, int depth) {
        if (depth > 32) {
            errors.add("include depth over 32 at " + path);
            return;
        }
        String dir = ShaderPackSource.dirOf(path);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean inComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lineRewrite.apply(lines[i]);
            // continuation lines join into one logical line
            while (line.endsWith("\\") && i + 1 < lines.length) line = line.substring(0, line.length() - 1) + " " + lineRewrite.apply(lines[++i]);
            String code = inComment ? null : line.stripLeading();
            boolean directive = !inComment && code.startsWith("#");
            inComment = commentStateAfter(line, inComment);
            if (!directive) {
                if (active()) out.append(line).append('\n');
                continue;
            }
            String d = code.substring(1).stripLeading();
            String word = firstWord(d);
            String rest = stripComments(d.substring(word.length())).trim();
            switch (word) {
                case "ifdef" -> push(active() && macros.containsKey(firstWord(rest)));
                case "ifndef" -> push(active() && !macros.containsKey(firstWord(rest)));
                case "if" -> push(active() && eval(rest, path));
                case "elif" -> {
                    int[] top = top(path, word);
                    if (top == null) break;
                    if (top[1] == 1 || top[2] == 0) top[0] = 0;
                    else if (eval(rest, path)) {
                        top[0] = 1;
                        top[1] = 1;
                    }
                }
                case "else" -> {
                    int[] top = top(path, word);
                    if (top == null) break;
                    top[0] = top[1] == 0 && top[2] == 1 ? 1 : 0;
                    top[1] = 1;
                }
                case "endif" -> {
                    if (condStack.isEmpty()) errors.add(path + ": #endif without #if");
                    else condStack.remove(condStack.size() - 1);
                }
                default -> {
                    if (!active()) break;
                    switch (word) {
                        case "include" -> {
                            String target = rest.replaceAll("^[\"<]|[\">]$", "");
                            String p = ShaderPackSource.normalize(target.startsWith("/") ? target : dir + "/" + target);
                            String t = files.apply(p);
                            if (t == null) errors.add(path + ": include not found " + target);
                            else include(p, t, depth + 1);
                        }
                        case "version" -> {
                            if (versionSeen) break;   // a second #version (an include's) is dropped
                            versionSeen = true;
                            out.append(line).append('\n');
                            for (String s : standard) out.append("#define ").append(s.trim()).append('\n');
                        }
                        case "define" -> {
                            define(rest);
                            out.append(line).append('\n');
                        }
                        case "undef" -> {
                            macros.remove(firstWord(rest));
                            out.append(line).append('\n');
                        }
                        case "error" -> errors.add(path + ": #error " + rest);
                        default -> out.append(line).append('\n');   // #extension, #pragma, #line, #warning
                    }
                }
            }
        }
    }

    private void push(boolean taken) {
        boolean parent = active();
        condStack.add(new int[] {taken ? 1 : 0, taken ? 1 : 0, parent ? 1 : 0});
    }

    private int[] top(String path, String word) {
        if (condStack.isEmpty()) {
            errors.add(path + ": #" + word + " without #if");
            return null;
        }
        return condStack.get(condStack.size() - 1);
    }

    private void define(String rest) {
        String name = identAt(rest, 0);
        if (name.isEmpty()) return;
        int at = name.length();
        List<String> params = null;
        if (at < rest.length() && rest.charAt(at) == '(') {   // function-like only when '(' follows the name directly
            int close = rest.indexOf(')', at);
            if (close < 0) return;
            params = new ArrayList<>();
            for (String p : rest.substring(at + 1, close).split(",")) if (!p.isBlank()) params.add(p.trim());
            at = close + 1;
        }
        macros.put(name, new Macro(name, params, stripComments(rest.substring(at)).trim()));
    }

    /** the block-comment state after this line (string-free GLSL: no quotes to care about) */
    static boolean commentStateAfter(String line, boolean in) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (in) {
                if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                    in = false;
                    i++;
                }
            } else if (c == '/' && i + 1 < line.length()) {
                char n = line.charAt(i + 1);
                if (n == '/') return false;
                if (n == '*') {
                    in = true;
                    i++;
                }
            }
        }
        return in;
    }

    static String stripComments(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') break;
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                if (e < 0) break;
                i = e + 1;
                b.append(' ');
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    private static String firstWord(String s) {
        int i = 0;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
        return s.substring(0, i);
    }

    private static String identAt(String s, int at) {
        int i = at;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
        return s.substring(at, i);
    }

    // ------------------------------------------------------------------ #if expressions

    private boolean eval(String expr, String path) {
        try {
            List<String> toks = expand(tokenize(expr), new HashSet<>(), true);
            Expr e = new Expr(toks);
            double v = e.ternary();
            if (e.pos != toks.size()) throw new IllegalArgumentException("trailing " + toks.subList(e.pos, toks.size()));
            return v != 0;
        } catch (RuntimeException ex) {
            errors.add(path + ": #if " + expr + ": " + ex.getMessage());
            return false;
        }
    }

    static List<String> tokenize(String s) {
        List<String> t = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                t.add(s.substring(i, j));
                i = j;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.'
                        || ((s.charAt(j) == '+' || s.charAt(j) == '-') && (s.charAt(j - 1) == 'e' || s.charAt(j - 1) == 'E') && !s.startsWith("0x", i)))) j++;
                t.add(s.substring(i, j));
                i = j;
            } else {
                String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                if (List.of("&&", "||", "==", "!=", "<=", ">=", "<<", ">>", "##").contains(two)) {
                    t.add(two);
                    i += 2;
                } else {
                    t.add(String.valueOf(c));
                    i++;
                }
            }
        }
        return t;
    }

    /** macro expansion of an #if expression's tokens; `defined X` / `defined(X)` resolved first */
    private List<String> expand(List<String> in, Set<String> hide, boolean resolveDefined) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            String tok = in.get(i);
            if (resolveDefined && tok.equals("defined")) {
                String name;
                if (i + 1 < in.size() && in.get(i + 1).equals("(")) {
                    name = in.get(i + 2);
                    i += 3;
                } else {
                    name = in.get(++i);
                }
                out.add(macros.containsKey(name) ? "1" : "0");
                continue;
            }
            Macro m = macros.get(tok);
            if (m == null || hide.contains(tok)) {
                out.add(tok);
                continue;
            }
            Set<String> h = new HashSet<>(hide);
            h.add(tok);
            if (m.params == null) {
                out.addAll(expand(tokenize(m.body), h, resolveDefined));
                continue;
            }
            if (i + 1 >= in.size() || !in.get(i + 1).equals("(")) {   // a function-like name without call: as is
                out.add(tok);
                continue;
            }
            List<List<String>> args = new ArrayList<>();
            List<String> cur = new ArrayList<>();
            int depth = 0, j = i + 2;
            for (; j < in.size(); j++) {
                String a = in.get(j);
                if (a.equals("(")) depth++;
                if (a.equals(")")) {
                    if (depth == 0) break;
                    depth--;
                }
                if (a.equals(",") && depth == 0) {
                    args.add(cur);
                    cur = new ArrayList<>();
                } else cur.add(a);
            }
            args.add(cur);
            i = j;
            List<String> body = tokenize(m.body), sub = new ArrayList<>();
            for (String b : body) {
                int k = m.params.indexOf(b);
                if (k >= 0 && k < args.size()) sub.addAll(expand(args.get(k), hide, resolveDefined));
                else sub.add(b);
            }
            out.addAll(expand(sub, h, resolveDefined));
        }
        return out;
    }

    /** precedence-climbing evaluator over doubles; integer division / modulo / shifts / bit ops on longs */
    private static final class Expr {
        final List<String> t;
        int pos;

        Expr(List<String> t) {
            this.t = t;
        }

        String peek() {
            return pos < t.size() ? t.get(pos) : "";
        }

        boolean eat(String s) {
            if (peek().equals(s)) {
                pos++;
                return true;
            }
            return false;
        }

        double ternary() {
            double c = or();
            if (eat("?")) {
                double a = ternary();
                if (!eat(":")) throw new IllegalArgumentException("':' expected");
                double b = ternary();
                return c != 0 ? a : b;
            }
            return c;
        }

        double or() {
            double v = and();
            while (eat("||")) {
                double r = and();
                v = (v != 0 || r != 0) ? 1 : 0;
            }
            return v;
        }

        double and() {
            double v = bitOr();
            while (eat("&&")) {
                double r = bitOr();
                v = (v != 0 && r != 0) ? 1 : 0;
            }
            return v;
        }

        double bitOr() {
            double v = bitXor();
            while (peek().equals("|")) {
                pos++;
                v = (long) v | (long) bitXor();
            }
            return v;
        }

        double bitXor() {
            double v = bitAnd();
            while (eat("^")) v = (long) v ^ (long) bitAnd();
            return v;
        }

        double bitAnd() {
            double v = equality();
            while (peek().equals("&")) {
                pos++;
                v = (long) v & (long) equality();
            }
            return v;
        }

        double equality() {
            double v = relational();
            while (true) {
                if (eat("==")) v = v == relational() ? 1 : 0;
                else if (eat("!=")) v = v != relational() ? 1 : 0;
                else return v;
            }
        }

        double relational() {
            double v = shift();
            while (true) {
                if (eat("<=")) v = v <= shift() ? 1 : 0;
                else if (eat(">=")) v = v >= shift() ? 1 : 0;
                else if (eat("<")) v = v < shift() ? 1 : 0;
                else if (eat(">")) v = v > shift() ? 1 : 0;
                else return v;
            }
        }

        double shift() {
            double v = additive();
            while (true) {
                if (eat("<<")) v = (long) v << (long) additive();
                else if (eat(">>")) v = (long) v >> (long) additive();
                else return v;
            }
        }

        double additive() {
            double v = mult();
            while (true) {
                if (eat("+")) v += mult();
                else if (eat("-")) v -= mult();
                else return v;
            }
        }

        double mult() {
            double v = unary();
            while (true) {
                if (eat("*")) v *= unary();
                else if (eat("/")) {
                    double r = unary();
                    v = isInt(v) && isInt(r) && r != 0 ? (double) ((long) v / (long) r) : v / r;
                } else if (eat("%")) {
                    double r = unary();
                    v = r == 0 ? 0 : (double) ((long) v % (long) r);
                } else return v;
            }
        }

        static boolean isInt(double d) {
            return d == Math.rint(d) && !Double.isInfinite(d);
        }

        double unary() {
            if (eat("!")) return unary() == 0 ? 1 : 0;
            if (eat("-")) return -unary();
            if (eat("+")) return unary();
            if (eat("~")) return ~(long) unary();
            return primary();
        }

        double primary() {
            if (eat("(")) {
                double v = ternary();
                if (!eat(")")) throw new IllegalArgumentException("')' expected");
                return v;
            }
            String s = peek();
            if (s.isEmpty()) throw new IllegalArgumentException("value expected");
            pos++;
            char c = s.charAt(0);
            if (Character.isDigit(c) || c == '.') return number(s);
            if (s.equals("true")) return 1;
            return 0;   // an identifier no macro defines (C: 0)
        }

        static double number(String s) {
            String n = s.toLowerCase();
            if (n.startsWith("0x")) return Long.parseLong(n.substring(2).replaceAll("[ul]+$", ""), 16);
            n = n.replaceAll("[ulf]+$", "");
            if (n.endsWith("lf")) n = n.substring(0, n.length() - 2);
            return Double.parseDouble(n);
        }
    }
}
