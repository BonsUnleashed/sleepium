package dev.lull.optsupport;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * Which methods of a class other mods' mixins change, per method name. The bytecode analysers (sleep engine, shape
 * purity) read classes from their resources, i.e. before mixins; this tells them where those bytes are no longer the
 * code that runs, so they can refuse just those methods instead of the whole class.
 *
 * <p>A method name counts as touched by a mixin when the mixin
 * <ul>
 *   <li>injects into it: any method annotation with a {@code method} attribute (Inject, Redirect, ModifyArg(s),
 *       ModifyVariable, ModifyConstant, and MixinExtras' Wrap/Modify* injectors), a wildcard meaning every method;</li>
 *   <li>overwrites it (@Overwrite), or merges a method of that name (unannotated or @Unique: it may override an
 *       inherited one); @Shadow, @Accessor and @Invoker change no existing code and are ignored.</li>
 * </ul>
 * Mixins of this mod (dev.lull.*, and the framework's dev.probe.*) are ignored: they are exactness-checked. Pending mixins of not yet loaded classes
 * are included. Any failure to read a mixin makes every method of its target count as touched.
 */
public final class MixinTouch {
    private static final String ALL = "*";
    private static final Map<String, Set<String>> TOUCHED = new ConcurrentHashMap<>();
    private static final Field PENDING = pendingField();

    private MixinTouch() {}

    /** Is method {@code name} of class {@code internalName} changed by some foreign mixin? */
    public static boolean touched(String internalName, String name) {
        Set<String> t = TOUCHED.computeIfAbsent(internalName, MixinTouch::compute);
        return t.contains(ALL) || t.contains(name);
    }

    /** Any foreign mixin at all on this class (for callers that cannot work per method). */
    public static boolean anyForeign(String internalName) {
        return !TOUCHED.computeIfAbsent(internalName, MixinTouch::compute).isEmpty();
    }

    private static volatile Map<String, Set<String>> INDEX;
    /** foreign @Overwrites by target class: the method names (filled with the index) */
    private static final Map<String, Set<String>> OVERWRITES = new java.util.HashMap<>();

    /** Is method {@code name} of this class overwritten by a foreign mixin (no injector of ours can go into it)? */
    public static boolean overwritten(String internalName, String name) {
        index();
        Set<String> o = OVERWRITES.get(internalName);
        return o != null && o.contains(name);
    }

    /** the names of a mixin class's @Overwrite methods */
    static List<String> overwriteNames(ClassNode cn) {
        List<String> out = new java.util.ArrayList<>();
        for (MethodNode m : cn.methods)
            for (AnnotationNode a : annotations(m)) if ("Lorg/spongepowered/asm/mixin/Overwrite;".equals(a.desc)) out.add(m.name);
        return out;
    }

    /** the target method names of a mixin method's injector annotations (empty: not an injector) */
    static List<String> injectorTargets(MethodNode m) {
        List<String> out = new java.util.ArrayList<>();
        for (AnnotationNode a : annotations(m)) {
            String d = a.desc;
            boolean injector = d.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !d.endsWith("/At;") && !d.endsWith("/Slice;")
                    || d.startsWith("Lcom/llamalad7/mixinextras/injector/");
            if (!injector || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                if (!"method".equals(a.values.get(i))) continue;
                Object v = a.values.get(i + 1);
                if (v instanceof List<?> l) for (Object o : l) out.add(nameOf(String.valueOf(o)));
                else out.add(nameOf(String.valueOf(v)));
            }
        }
        return out;
    }

    /** foreign @Redirects by target class: "method|invoked name" (filled with the index) */
    private static final Map<String, Set<String>> REDIRECTS = new java.util.HashMap<>();

    /** Does a foreign mixin @Redirect the call to {@code invoked} inside {@code method} of this class? */
    public static boolean redirected(String internalName, String method, String invoked) {
        index();
        Set<String> r = REDIRECTS.get(internalName);
        return r != null && (r.contains(method + "|" + invoked) || r.contains(ALL + "|" + invoked));
    }

    /** "method|invoked name" of every @Redirect of a mixin class (the invoked name from its @At target). */
    static List<String> redirectKeys(ClassNode cn) {
        List<String> out = new java.util.ArrayList<>();
        for (MethodNode m : cn.methods)
            for (AnnotationNode a : annotations(m)) {
                if (!"Lorg/spongepowered/asm/mixin/injection/Redirect;".equals(a.desc) || a.values == null) continue;
                List<String> methods = new java.util.ArrayList<>();
                String invoked = null;
                for (int i = 0; i + 1 < a.values.size(); i += 2) {
                    Object v = a.values.get(i + 1);
                    if ("method".equals(a.values.get(i))) {
                        if (v instanceof List<?> l) for (Object o : l) methods.add(nameOf(String.valueOf(o)));
                        else methods.add(nameOf(String.valueOf(v)));
                    } else if ("at".equals(a.values.get(i)) && v instanceof AnnotationNode at && at.values != null) {
                        for (int k = 0; k + 1 < at.values.size(); k += 2)
                            if ("target".equals(at.values.get(k))) invoked = invokedName(String.valueOf(at.values.get(k + 1)));
                    }
                }
                if (invoked != null) for (String mm : methods) out.add(mm + "|" + invoked);
            }
        return out;
    }

    /** "Lowner;name(desc)ret" or "owner.name(...)" -> name */
    static String invokedName(String target) {
        String s = target.trim();
        int paren = s.indexOf('(');
        if (paren >= 0) s = s.substring(0, paren);
        int colon = s.indexOf(':');   // a field: "Lowner;name:Ltype;"
        if (colon >= 0) s = s.substring(0, colon);
        int cut = Math.max(s.lastIndexOf(';'), s.lastIndexOf('.'));
        return cut >= 0 ? s.substring(cut + 1) : s;
    }

    /**
     * Every foreign mixin of every registered config, by target class, read from the configs' class lists and the
     * mixins' bytecode: complete even while Mixin has not prepared the other mods' configs yet (ours are prepared first,
     * and the plugins decide then).
     */
    private static synchronized Map<String, Set<String>> index() {
        Map<String, Set<String>> idx = INDEX;
        if (idx != null) return idx;
        Map<String, Set<String>> m = new java.util.HashMap<>();
        indexModFiles(m);
        try {
            var provider = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider();
            for (org.spongepowered.asm.mixin.transformer.Config c : org.spongepowered.asm.mixin.Mixins.getConfigs()) {
                org.spongepowered.asm.mixin.extensibility.IMixinConfig mc = c.getConfig();
                if (mc == null) continue;
                if (mc.getName().startsWith("sleepium") || mc.getName().startsWith("probe")) continue;   // (the engine's and its framework's)
                String pkg = mc.getMixinPackage();
                if (pkg == null) continue;
                if (!pkg.isEmpty() && !pkg.endsWith(".")) pkg += ".";
                for (String field : new String[] {"mixinClasses", "mixinClassesClient", "mixinClassesServer"}) {
                    Field f = mc.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    Object list = f.get(mc);
                    if (!(list instanceof List<?> names)) continue;
                    for (Object n : names) {
                        String cls = pkg + n;
                        if (cls.startsWith("dev.lull.") || cls.startsWith("dev.probe.")) continue;
                        ClassNode cn;
                        try {
                            cn = provider.getClassNode(cls);
                        } catch (Throwable t) {
                            continue;
                        }
                        Set<String> methods = new HashSet<>();
                        collect(cn, methods);
                        List<String> reds = redirectKeys(cn), ows = overwriteNames(cn);
                        for (String target : targets(cn)) {
                            m.computeIfAbsent(target, k -> new HashSet<>()).addAll(methods);
                            if (!reds.isEmpty()) REDIRECTS.computeIfAbsent(target, k -> new HashSet<>()).addAll(reds);
                            if (!ows.isEmpty()) OVERWRITES.computeIfAbsent(target, k -> new HashSet<>()).addAll(ows);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[sleep/opt] foreign mixin index incomplete: " + t);
        }
        INDEX = m;
        System.out.println("[sleep/opt] foreign mixin index: " + m.size() + " target classes; Block: " + m.get("net/minecraft/world/level/block/Block"));
        return m;
    }

    /**
     * Every mod file's mixin configs as FML lists them (jar-in-jar mods included), read from the files themselves: known
     * before Mixin registers or prepares any of them.
     */
    private static void indexModFiles(Map<String, Set<String>> m) {
        try {
            var mods = net.neoforged.fml.loading.LoadingModList.get();
            if (mods == null) return;
            for (var info : mods.getModFiles()) {
                var file = info.getFile();
                for (var cfg : file.getMixinConfigs()) {
                    String name = cfg.config();
                    if (Boolean.getBoolean("sleep.mixinIndex.debug")) System.out.println("[sleep/opt] mixin index: " + info.moduleName() + " " + name + " -> " + file.findResource(name));
                    if (name.startsWith("sleepium") || name.startsWith("probe")) continue;
                    java.nio.file.Path json = file.findResource(name);
                    if (json == null || !java.nio.file.Files.exists(json)) continue;
                    com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(json)).getAsJsonObject();
                    if (!o.has("package")) continue;
                    String pkg = o.get("package").getAsString();
                    if (pkg.startsWith("dev.lull") || pkg.startsWith("dev.probe")) continue;
                    for (String key : new String[] {"mixins", "client", "server"}) {
                        if (!o.has(key) || !o.get(key).isJsonArray()) continue;
                        for (var e : o.getAsJsonArray(key)) {
                            java.nio.file.Path bytes = file.findResource((pkg + "." + e.getAsString()).replace('.', '/') + ".class");
                            if (bytes != null && java.nio.file.Files.exists(bytes)) indexClass(java.nio.file.Files.readAllBytes(bytes), m);
                        }
                    }
                    // a config plugin may add mixins of its package the lists do not name (its getMixins: Embeddium's
                    // lists are empty): every @Mixin class under the package counts (one the plugin then declines too)
                    if (o.has("plugin")) {
                        java.nio.file.Path root = file.findResource(pkg.replace('.', '/'));
                        if (root != null && java.nio.file.Files.isDirectory(root))
                            try (var walk = java.nio.file.Files.walk(root)) {
                                for (java.nio.file.Path c : (Iterable<java.nio.file.Path>) walk::iterator)
                                    if (c.toString().endsWith(".class")) indexClass(java.nio.file.Files.readAllBytes(c), m);
                            }
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[sleep/opt] foreign mixin index (mod files) incomplete: " + t);
        }
    }

    /** Every mixin class of this mod's configs (sleepium*.mixins.json), read from the mod file (no code). */
    static void ourMixins(java.util.function.BiConsumer<String, ClassNode> each) {
        try {
            var mods = net.neoforged.fml.loading.LoadingModList.get();
            if (mods == null) return;
            for (var info : mods.getModFiles()) {
                var file = info.getFile();
                for (var cfg : file.getMixinConfigs()) {
                    String name = cfg.config();
                    if (!name.startsWith("sleepium")) continue;
                    java.nio.file.Path json = file.findResource(name);
                    if (json == null || !java.nio.file.Files.exists(json)) continue;
                    com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(json)).getAsJsonObject();
                    String pkg = o.get("package").getAsString();
                    for (String key : new String[] {"mixins", "client", "server"}) {
                        if (!o.has(key) || !o.get(key).isJsonArray()) continue;
                        for (var e : o.getAsJsonArray(key)) {
                            String cls = pkg + "." + e.getAsString();
                            java.nio.file.Path bytes = file.findResource(cls.replace('.', '/') + ".class");
                            if (bytes == null || !java.nio.file.Files.exists(bytes)) continue;
                            ClassNode cn = new ClassNode();
                            new org.objectweb.asm.ClassReader(java.nio.file.Files.readAllBytes(bytes)).accept(cn, org.objectweb.asm.ClassReader.SKIP_CODE);
                            each.accept(cls, cn);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[sleep/opt] own mixin list incomplete: " + t);
        }
    }

    /** One foreign mixin class's touches, by its target classes (a class without @Mixin adds nothing). */
    private static void indexClass(byte[] bytes, Map<String, Set<String>> m) {
        ClassNode cn = new ClassNode();
        new org.objectweb.asm.ClassReader(bytes).accept(cn, org.objectweb.asm.ClassReader.SKIP_CODE);
        List<String> ts = targets(cn);
        if (ts.isEmpty()) return;
        Set<String> methods = new HashSet<>();
        collect(cn, methods);
        List<String> reds = redirectKeys(cn), ows = overwriteNames(cn);
        for (String target : ts) {
            m.computeIfAbsent(target, k -> new HashSet<>()).addAll(methods);
            if (!reds.isEmpty()) REDIRECTS.computeIfAbsent(target, k -> new HashSet<>()).addAll(reds);
            if (!ows.isEmpty()) OVERWRITES.computeIfAbsent(target, k -> new HashSet<>()).addAll(ows);
        }
    }

    /** The target classes (internal names) of a mixin class: @Mixin's value and targets. */
    static List<String> targets(ClassNode cn) {
        List<String> out = new java.util.ArrayList<>();
        List<AnnotationNode> as = new java.util.ArrayList<>();
        if (cn.visibleAnnotations != null) as.addAll(cn.visibleAnnotations);
        if (cn.invisibleAnnotations != null) as.addAll(cn.invisibleAnnotations);
        for (AnnotationNode a : as) {
            if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(a.desc) || a.values == null) continue;
            for (int i = 0; i + 1 < a.values.size(); i += 2) {
                Object v = a.values.get(i + 1);
                if (!(v instanceof List<?> l)) continue;
                for (Object o : l) {
                    if (o instanceof org.objectweb.asm.Type t) out.add(t.getInternalName());
                    else if (o instanceof String str) out.add(str.replace('.', '/'));
                }
            }
        }
        return out;
    }

    private static Set<String> compute(String internalName) {
        Set<String> out = new HashSet<>();
        Set<String> indexed = index().get(internalName);
        if (indexed != null) out.addAll(indexed);
        try {
            ClassInfo info = ClassInfo.fromCache(internalName);
            if (info == null) return out.isEmpty() ? Set.of() : Set.copyOf(out);   // not in Mixin's cache yet: the index alone
            Set<IMixinInfo> mixins = new HashSet<>(info.getAppliedMixins());
            if (PENDING != null) {
                Object p = PENDING.get(info);
                if (p instanceof Collection<?> c) for (Object o : c) if (o instanceof IMixinInfo mi) mixins.add(mi);
            }
            for (IMixinInfo mi : mixins) {
                if (mi.getClassName().startsWith("dev.lull.") || mi.getClassName().startsWith("dev.probe.")) continue;   // (the engine's and its framework's)
                collect(mi, out);
            }
        } catch (Throwable t) {
            out.add(ALL);
        }
        return out.isEmpty() ? Set.of() : Set.copyOf(out);
    }

    private static void collect(IMixinInfo mi, Set<String> out) {
        ClassNode cn;
        try {
            cn = mi.getClassNode(0);
        } catch (Throwable t) {
            cn = null;
        }
        if (cn == null) {
            out.add(ALL);
            return;
        }
        collect(cn, out);
    }

    private static void collect(ClassNode cn, Set<String> out) {
        for (MethodNode m : cn.methods) {
            if (m.name.equals("<clinit>")) continue;
            boolean injector = false, passive = false;
            for (AnnotationNode a : annotations(m)) {
                String d = a.desc;
                if (d.endsWith("/Shadow;") || d.endsWith("/Accessor;") || d.endsWith("/Invoker;")) passive = true;
                if (d.endsWith("/Overwrite;")) out.add(m.name);
                boolean injectorType = d.startsWith("Lorg/spongepowered/asm/mixin/injection/") && !d.endsWith("/At;") && !d.endsWith("/Slice;")
                        || d.startsWith("Lcom/llamalad7/mixinextras/injector/");
                boolean named = false;
                List<Object> v = a.values;
                if (v != null) {
                    for (int i = 0; i + 1 < v.size(); i += 2) {
                        if (!"method".equals(v.get(i))) continue;
                        named = injector = true;
                        Object targets = v.get(i + 1);
                        if (targets instanceof List<?> l) for (Object s : l) out.add(nameOf(String.valueOf(s)));
                        else out.add(nameOf(String.valueOf(targets)));
                    }
                }
                if (injectorType && !named) {   // targets given another way (@Desc): cannot tell which method
                    injector = true;
                    out.add(ALL);
                }
            }
            if (!injector && !passive && !m.name.startsWith("lambda$")) out.add(m.name.equals("<init>") ? "<init>" : m.name);
        }
    }

    /** "name", "name(desc)ret", "Lowner;name(desc)ret" or a pattern -> the method name, or ALL if unclear. */
    static String nameOf(String target) {
        String s = target.trim();
        int semi = s.indexOf(';'), paren = s.indexOf('(');
        if (s.startsWith("L") && semi > 0 && (paren < 0 || semi < paren)) s = s.substring(semi + 1);
        int end = s.length();
        for (char c : new char[] {'(', ':'}) {
            int k = s.indexOf(c);
            if (k >= 0 && k < end) end = k;
        }
        s = s.substring(0, end);
        return s.isEmpty() || s.contains("*") || s.contains("/") || s.contains(" ") ? ALL : s;
    }

    private static List<AnnotationNode> annotations(MethodNode m) {
        List<AnnotationNode> all = new java.util.ArrayList<>();
        if (m.visibleAnnotations != null) all.addAll(m.visibleAnnotations);
        if (m.invisibleAnnotations != null) all.addAll(m.invisibleAnnotations);
        return all;
    }

    private static Field pendingField() {
        try {
            Field f = ClassInfo.class.getDeclaredField("mixins");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Diagnostics: the touched method names of a class. */
    public static Set<String> touchedMethods(String internalName) {
        return TOUCHED.computeIfAbsent(internalName, MixinTouch::compute);
    }
}
