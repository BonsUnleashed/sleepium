package dev.probe.optsupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;

/**
 * GeckoLib's animation files read as a stream (10-11; {@code -Dlab.opt.AnimStreamMixin=true}): GeckoLib reads a file
 * whole into a String and parses it into one Gson tree before baking - for the game's body file (100 MB, 5000 clips)
 * that tree is ten times the file and a 2 GB client ran out of memory at the load. Here the file is read with a
 * JsonReader and each animation parsed and baked on its own, by GeckoLib's own adapter (GEO_GSON's BakedAnimations
 * deserializer on a one-animation object), the baked ones gathered into one BakedAnimations: the same animations,
 * the largest tree one clip's. The keyframe tables (KeyframeInternMixin) are shared across the file's animations and
 * emptied when it is done ({@link #batch}).
 */
public final class AnimStream {
    private AnimStream() {}

    /** a streamed file is being baked (the tables are kept until its end) */
    public static final ThreadLocal<Boolean> BATCH = ThreadLocal.withInitial(() -> false);
    /** KeyframeInternMixin's tables: a value's Constant, a keyframe's first equal one */
    public static volatile Map<Double, Object> CONSTANTS = new java.util.concurrent.ConcurrentHashMap<>();
    public static volatile Map<Object, Object> KEYFRAMES = new java.util.concurrent.ConcurrentHashMap<>();
    public static long files, animations;

    public static void clearTables() {   // (new tables: a cleared one keeps its grown array - 40 MB of empty slots)
        CONSTANTS = new java.util.concurrent.ConcurrentHashMap<>();
        KEYFRAMES = new java.util.concurrent.ConcurrentHashMap<>();
    }

    public static boolean batch() {
        return BATCH.get();
    }

    public static BakedAnimations load(ResourceLocation location, ResourceManager manager) throws java.io.IOException {
        var res = manager.getResource(location).orElseThrow(() -> new java.io.FileNotFoundException(location.toString()));
        Map<String, Animation> out = new HashMap<>();
        BATCH.set(true);
        try (var in = res.open(); JsonReader r = new JsonReader(new InputStreamReader(new java.io.BufferedInputStream(in, 1 << 16), StandardCharsets.UTF_8))) {
            r.setLenient(true);
            r.beginObject();
            while (r.hasNext()) {
                if (!r.nextName().equals("animations")) {
                    r.skipValue();
                    continue;
                }
                r.beginObject();
                while (r.hasNext()) {
                    String name = r.nextName();
                    JsonObject one = new JsonObject();
                    one.add(name, JsonParser.parseReader(r));
                    BakedAnimations b = KeyFramesAdapter.GEO_GSON.fromJson(one, BakedAnimations.class);
                    if (b != null) out.putAll(b.animations());
                    animations++;
                }
                r.endObject();
            }
            r.endObject();
        } finally {
            BATCH.set(false);
            clearTables();
        }
        files++;
        return new BakedAnimations(out);
    }
}
