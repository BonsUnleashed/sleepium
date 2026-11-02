package dev.sleep.shaders;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Legacy (compatibility profile, #version 110..150 and "compatibility") shaderpack GLSL to the core profile Minecraft's
 * context has, on tokens: the built-ins of the fixed-function era become declarations this engine feeds (Iris's
 * core-profile names: attributes bound by name, uniforms set per draw), {@code attribute/varying} become {@code in/out},
 * {@code gl_FragData[n] / gl_FragColor} become {@code layout(location = n) out}, the removed texture functions become
 * the current ones. Comments and preprocessor lines pass through (a #define body is code too: rewritten the same way).
 *
 * <p>What the engine provides for the result: attributes {@code vaPosition (vec3) vaColor (vec4) vaUV0 (vec2)
 * vaUV1 (ivec2) vaUV2 (ivec2) vaNormal (vec3)}, uniforms {@code modelViewMatrix projectionMatrix (mat4) normalMatrix
 * (mat3) textureMatrix sl_LightmapTextureMat (mat4) sl_Fog (struct)}; see {@link #ATTRIBUTES}.
 */
public final class GlslTranslator {
    public enum Stage { VERTEX, GEOMETRY, FRAGMENT, COMPUTE }

    /** attribute name -> GLSL type, in binding order (location = index) */
    public static final String[][] ATTRIBUTES = {{"vaPosition", "vec3"}, {"vaColor", "vec4"}, {"vaUV0", "vec2"}, {"vaUV1", "ivec2"},
            {"vaUV2", "ivec2"}, {"vaNormal", "vec3"}};

    /** uniforms this engine sets for every program (Iris's core-profile names): declared when used but not declared */
    public static final String[][] ENGINE_UNIFORMS = {{"modelViewMatrix", "mat4"}, {"projectionMatrix", "mat4"}, {"normalMatrix", "mat3"},
            {"textureMatrix", "mat4"}, {"chunkOffset", "vec3"}, {"modelViewMatrixInverse", "mat4"}, {"projectionMatrixInverse", "mat4"}};

    static final Set<String> QUALIFIERS = Set.of("in", "out", "uniform", "attribute", "varying", "flat", "smooth", "noperspective", "centroid", "const");
    static final Set<String> PRECISION = Set.of("highp", "mediump", "lowp", "layout");

    /** the result: text plus what it uses */
    public record Result(String text, int version, Set<Integer> fragOutputs, Set<String> usedAttributes, List<String> notes) {}

    private GlslTranslator() {}

    public static Result translate(String src, Stage stage) {
        return new GlslTranslator0(src, stage).run();
    }

    private static final class GlslTranslator0 {
        final String src;
        final Stage stage;
        final List<Tok> toks;
        final Set<Integer> fragOut = new TreeSet<>();
        final Set<String> attrs = new LinkedHashSet<>();
        final Set<String> uniforms = new LinkedHashSet<>();
        final Set<String> helpers = new LinkedHashSet<>();
        final List<String> notes = new ArrayList<>();
        boolean usesFog, usesFogCoord, usesFrontColor, usesTexCoordArray, samplerNamedTexture;
        int version = 110;
        boolean compatibility;

        GlslTranslator0(String src, Stage stage) {
            this.src = src;
            this.stage = stage;
            this.toks = lex(src);
        }

        Result run() {
            readVersion();
            samplerNamedTexture = declaresSamplerNamed("texture");
            StringBuilder body = new StringBuilder();
            int n = toks.size();
            for (int i = 0; i < n; i++) {
                Tok t = toks.get(i);
                if (t.kind != Tok.IDENT) {
                    if (t.kind == Tok.DIRECTIVE && t.text.stripLeading().startsWith("#version")) continue;   // re-emitted on top
                    if (t.kind == Tok.DIRECTIVE) body.append(rewriteDirective(t.text));
                    else body.append(t.text);
                    continue;
                }
                if (i > 0 && prevSignificant(i).equals(".")) {   // a member (.xyz, struct field): never a built-in
                    body.append(t.text);
                    continue;
                }
                int[] next = {i};
                String r = rewrite(t.text, i, next);
                body.append(r);
                i = next[0];
            }
            int outVersion = Math.max(version, stage == Stage.COMPUTE ? 430 : 330);
            StringBuilder head = new StringBuilder("#version ").append(outVersion).append(" core\n");
            StringBuilder decl = new StringBuilder();
            // Iris's core-profile names a pack may use without declaring them; nothing the pack declares itself twice
            Set<String> declared = declaredNames(), used = usedIdents();
            for (String[] u : ENGINE_UNIFORMS)
                if (used.contains(u[0]) && !declared.contains(u[0])) uniforms.add("uniform " + u[1] + " " + u[0] + ";");
            uniforms.removeIf(u -> declared.contains(u.substring(u.lastIndexOf(' ') + 1, u.length() - 1)));
            if (stage == Stage.VERTEX) {
                for (String[] a : ATTRIBUTES) if (used.contains(a[0])) attrs.add(a[0]);
                for (String[] a : ATTRIBUTES)
                    if (attrs.contains(a[0]) && !declared.contains(a[0])) decl.append("in ").append(a[1]).append(' ').append(a[0]).append(";\n");
                if (usesFrontColor) decl.append("out vec4 sl_FrontColor;\n");
                if (usesTexCoordArray) decl.append("out vec4 sl_TexCoord[8];\n");
                if (usesFogCoord) decl.append("out float sl_FogFragCoord;\n");
            } else if (stage == Stage.FRAGMENT) {
                if (usesFrontColor) decl.append("in vec4 sl_FrontColor;\n");
                if (usesTexCoordArray) decl.append("in vec4 sl_TexCoord[8];\n");
                if (usesFogCoord) decl.append("in float sl_FogFragCoord;\n");
                for (int o : fragOut) decl.append("layout(location = ").append(o).append(") out vec4 sl_FragData").append(o).append(";\n");
            }
            for (String u : uniforms) decl.append(u).append('\n');
            if (usesFog)
                decl.append("struct sl_FogParameters { vec4 color; float density; float start; float end; float scale; };\nuniform sl_FogParameters sl_Fog;\n");
            for (String h : helpers) decl.append(h).append('\n');
            String text = insertAfterHead(body.toString(), head.toString(), decl.toString());
            return new Result(text, outVersion, fragOut, attrs, notes);
        }

        // ------------------------------------------------------------------ rewrites

        String rewrite(String id, int i, int[] next) {
            switch (id) {
                case "attribute":
                    return stage == Stage.VERTEX ? "in" : id;
                case "varying":
                    return stage == Stage.VERTEX ? "out" : stage == Stage.FRAGMENT ? "in" : id;
                case "texture2D", "texture3D", "texture1D", "textureCube", "texture2DRect":
                    return isCall(i) ? "texture" : id;
                case "texture2DLod", "texture3DLod", "texture1DLod", "textureCubeLod":
                    return isCall(i) ? "textureLod" : id;
                case "texture2DGrad", "texture2DGradARB", "texture3DGrad":
                    return isCall(i) ? "textureGrad" : id;
                case "texture2DProj", "texture2DProjLod":
                    return isCall(i) ? (id.endsWith("Lod") ? "textureProjLod" : "textureProj") : id;
                case "texelFetch2D", "texelFetch3D":
                    return isCall(i) ? "texelFetch" : id;
                case "textureSize2D", "textureSize3D":
                    return isCall(i) ? "textureSize" : id;
                case "shadow2D", "shadow2DLod":
                    if (!isCall(i)) return id;
                    helpers.add(id.equals("shadow2D") ? "vec4 sl_shadow2D(sampler2DShadow s, vec3 c) { return vec4(texture(s, c)); }\n"
                            + "vec4 sl_shadow2D(sampler2DShadow s, vec3 c, float b) { return vec4(texture(s, c, b)); }"
                            : "vec4 sl_shadow2DLod(sampler2DShadow s, vec3 c, float l) { return vec4(textureLod(s, c, l)); }");
                    return "sl_" + id;
                case "texture":
                    // a #version 120 pack's atlas sampler named "texture" (a function in 130+): renamed where it is not called
                    return samplerNamedTexture && !isCall(i) ? "gtexture" : id;
                case "gl_FragColor":
                    if (stage != Stage.FRAGMENT) return id;
                    fragOut.add(0);
                    return "sl_FragData0";
                case "gl_FragData": {
                    if (stage != Stage.FRAGMENT) return id;
                    int a = nextSignificant(i), b = a < 0 ? -1 : nextSignificant(a), c = b < 0 ? -1 : nextSignificant(b);
                    if (a >= 0 && toks.get(a).text.equals("[") && b >= 0 && c >= 0 && toks.get(c).text.equals("]") && toks.get(b).text.matches("\\d+")) {
                        int k = Integer.parseInt(toks.get(b).text);
                        fragOut.add(k);
                        next[0] = c;
                        return "sl_FragData" + k;
                    }
                    notes.add("gl_FragData with a non-constant index");
                    return id;
                }
                case "gl_Vertex":
                    if (stage != Stage.VERTEX) return id;
                    attrs.add("vaPosition");
                    uniform("uniform vec3 chunkOffset;");   // terrain: section-relative positions (0 for everything else)
                    return "vec4(vaPosition + chunkOffset, 1.0)";
                case "gl_Color":
                    if (stage == Stage.VERTEX) {
                        attrs.add("vaColor");
                        return "vaColor";
                    }
                    if (stage == Stage.FRAGMENT) {
                        usesFrontColor = true;
                        return "sl_FrontColor";
                    }
                    return id;
                case "gl_FrontColor":
                    usesFrontColor = true;
                    return "sl_FrontColor";
                case "gl_MultiTexCoord0":
                    attrs.add("vaUV0");
                    return "vec4(vaUV0, 0.0, 1.0)";
                case "gl_MultiTexCoord1", "gl_MultiTexCoord2":
                    attrs.add("vaUV2");
                    return "vec4(vec2(vaUV2), 0.0, 1.0)";
                case "gl_Normal":
                    attrs.add("vaNormal");
                    return "vaNormal";
                case "gl_TexCoord":
                    usesTexCoordArray = true;
                    return "sl_TexCoord";
                case "gl_FogFragCoord":
                    usesFogCoord = true;
                    return "sl_FogFragCoord";
                case "gl_Fog":
                    usesFog = true;
                    return "sl_Fog";
                case "gl_ModelViewMatrix":
                    uniform("uniform mat4 modelViewMatrix;");
                    return "modelViewMatrix";
                case "gl_ProjectionMatrix":
                    uniform("uniform mat4 projectionMatrix;");
                    return "projectionMatrix";
                case "gl_ModelViewProjectionMatrix":
                    uniform("uniform mat4 modelViewMatrix;");
                    uniform("uniform mat4 projectionMatrix;");
                    return "(projectionMatrix * modelViewMatrix)";
                case "gl_ModelViewMatrixInverse":
                    uniform("uniform mat4 modelViewMatrix;");
                    return "inverse(modelViewMatrix)";
                case "gl_ProjectionMatrixInverse":
                    uniform("uniform mat4 projectionMatrix;");
                    return "inverse(projectionMatrix)";
                case "gl_NormalMatrix":
                    uniform("uniform mat3 normalMatrix;");
                    return "normalMatrix";
                case "gl_TextureMatrix": {
                    int a = nextSignificant(i), b = a < 0 ? -1 : nextSignificant(a), c = b < 0 ? -1 : nextSignificant(b);
                    if (a >= 0 && toks.get(a).text.equals("[") && c >= 0 && toks.get(c).text.equals("]") && toks.get(b).text.matches("\\d+")) {
                        next[0] = c;
                        if (toks.get(b).text.equals("0")) {
                            uniform("uniform mat4 textureMatrix;");
                            return "textureMatrix";
                        }
                        uniform("uniform mat4 sl_LightmapTextureMat;");
                        return "sl_LightmapTextureMat";
                    }
                    notes.add("gl_TextureMatrix with a non-constant index");
                    return id;
                }
                case "ftransform":
                    if (!isCall(i)) return id;
                    attrs.add("vaPosition");
                    uniform("uniform mat4 modelViewMatrix;");
                    uniform("uniform mat4 projectionMatrix;");
                    uniform("uniform vec3 chunkOffset;");
                    helpers.add("#define ftransform() (projectionMatrix * (modelViewMatrix * vec4(vaPosition + chunkOffset, 1.0)))");
                    return id;
                default:
                    return id;
            }
        }

        /** names the source declares: the identifier after a type that follows in / out / uniform / attribute / varying */
        Set<String> declaredNames() {
            Set<String> out = new java.util.HashSet<>();
            for (int i = 0; i < toks.size(); i++) {
                Tok t = toks.get(i);
                if (t.kind != Tok.IDENT || !QUALIFIERS.contains(t.text)) continue;
                int a = nextSignificant(i);
                while (a >= 0 && toks.get(a).kind == Tok.IDENT && (QUALIFIERS.contains(toks.get(a).text) || PRECISION.contains(toks.get(a).text)))
                    a = nextSignificant(a);
                int b = a < 0 ? -1 : nextSignificant(a);
                if (b >= 0 && toks.get(b).kind == Tok.IDENT) out.add(toks.get(b).text);
            }
            return out;
        }

        /** every identifier outside comments (a #define body uses names too) */
        Set<String> usedIdents() {
            Set<String> out = new java.util.HashSet<>();
            for (Tok t : toks) {
                if (t.kind == Tok.IDENT) out.add(t.text);
                else if (t.kind == Tok.DIRECTIVE) for (Tok d : lex(t.text.stripLeading().substring(1))) if (d.kind == Tok.IDENT) out.add(d.text);
            }
            return out;
        }

        void uniform(String decl) {
            uniforms.add(decl);
        }

        /** a #define line's body is code: its built-ins are rewritten the same way (a nested lex of the line) */
        String rewriteDirective(String line) {
            String s = line.stripLeading();
            if (!s.startsWith("#")) return line;
            String d = s.substring(1).stripLeading();
            if (!d.startsWith("define")) return line;
            GlslTranslator0 sub = new GlslTranslator0(line, stage);
            sub.samplerNamedTexture = samplerNamedTexture;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < sub.toks.size(); i++) {
                Tok t = sub.toks.get(i);
                if (t.kind != Tok.IDENT || (i > 0 && sub.prevSignificant(i).equals("."))) {
                    b.append(t.text);
                    continue;
                }
                int[] next = {i};
                b.append(sub.rewrite(t.text, i, next));
                i = next[0];
            }
            fragOut.addAll(sub.fragOut);
            attrs.addAll(sub.attrs);
            uniforms.addAll(sub.uniforms);
            helpers.addAll(sub.helpers);
            usesFog |= sub.usesFog;
            usesFogCoord |= sub.usesFogCoord;
            usesFrontColor |= sub.usesFrontColor;
            usesTexCoordArray |= sub.usesTexCoordArray;
            return b.toString();
        }

        boolean isCall(int i) {
            int n = nextSignificant(i);
            return n >= 0 && toks.get(n).text.equals("(");
        }

        int nextSignificant(int i) {
            for (int j = i + 1; j < toks.size(); j++) if (toks.get(j).kind != Tok.SPACE && toks.get(j).kind != Tok.COMMENT) return j;
            return -1;
        }

        String prevSignificant(int i) {
            for (int j = i - 1; j >= 0; j--) if (toks.get(j).kind != Tok.SPACE && toks.get(j).kind != Tok.COMMENT) return toks.get(j).text;
            return "";
        }

        boolean declaresSamplerNamed(String name) {
            for (int i = 0; i < toks.size(); i++) {
                Tok t = toks.get(i);
                if (t.kind == Tok.IDENT && t.text.equals(name) && prevSignificant(i).startsWith("sampler")) return true;
            }
            return false;
        }

        void readVersion() {
            for (Tok t : toks) {
                if (t.kind != Tok.DIRECTIVE) continue;
                String s = t.text.strip();
                if (!s.startsWith("#")) continue;
                String d = s.substring(1).strip();
                if (!d.startsWith("version")) continue;
                String[] p = d.split("\\s+");
                if (p.length > 1) version = Integer.parseInt(p[1].replaceAll("\\D", ""));
                compatibility = p.length > 2 && p[2].equals("compatibility");
                return;
            }
        }

        /** head (#version) first, then the original #extension lines that lead the file, then the declarations */
        static String insertAfterHead(String body, String head, String decl) {
            String[] lines = body.split("\n", -1);
            StringBuilder lead = new StringBuilder(), rest = new StringBuilder();
            int i = 0;
            boolean inComment = false;
            // the leading block: blank lines, comments, #extension / #define / other directives before the first code line
            for (; i < lines.length; i++) {
                String l = lines[i].strip();
                boolean code = !inComment && !l.isEmpty() && !l.startsWith("#") && !l.startsWith("//") && !l.startsWith("/*");
                if (code) break;
                lead.append(lines[i]).append('\n');
                inComment = GlslPreprocessor.commentStateAfter(lines[i], inComment);
            }
            for (; i < lines.length; i++) rest.append(lines[i]).append(i + 1 < lines.length ? "\n" : "");
            return head + lead + decl + rest;
        }
    }

    // ------------------------------------------------------------------ lexer (keeps every character)

    static final class Tok {
        static final int IDENT = 0, SPACE = 1, COMMENT = 2, DIRECTIVE = 3, OTHER = 4;
        final int kind;
        final String text;

        Tok(int kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    static List<Tok> lex(String s) {
        List<Tok> out = new ArrayList<>();
        int i = 0, n = s.length();
        boolean lineStart = true;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '#' && lineStart) {   // a preprocessor line, up to its end (continuations joined already)
                int e = s.indexOf('\n', i);
                if (e < 0) e = n;
                out.add(new Tok(Tok.DIRECTIVE, s.substring(i, e)));
                i = e;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                int e = s.indexOf('\n', i);
                if (e < 0) e = n;
                out.add(new Tok(Tok.COMMENT, s.substring(i, e)));
                i = e;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                e = e < 0 ? n : e + 2;
                out.add(new Tok(Tok.COMMENT, s.substring(i, e)));
                i = e;
                lineStart = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                int j = i;
                while (j < n && Character.isWhitespace(s.charAt(j))) {
                    if (s.charAt(j) == '\n') lineStart = true;
                    j++;
                }
                out.add(new Tok(Tok.SPACE, s.substring(i, j)));
                i = j;
                continue;
            }
            lineStart = false;
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                out.add(new Tok(Tok.IDENT, s.substring(i, j)));
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                out.add(new Tok(Tok.OTHER, s.substring(i, j)));
                i = j;
                continue;
            }
            out.add(new Tok(Tok.OTHER, String.valueOf(c)));
            i++;
        }
        return out;
    }
}
