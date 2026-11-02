package dev.sleep.shaders;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/**
 * The standard uniforms (OptiFine / Iris names) as values per frame, and their upload into any pack program: a program
 * lists its active uniforms once (glGetActiveUniform), each is matched by name to a frame value, a custom uniform, a
 * per-draw value or a sampler unit. Per frame every program gets its values once, on its first use in the frame.
 */
public final class PackUniforms {
    /** frame values by name: float[] (1..16 components; ints and bools as floats) */
    public final Map<String, float[]> frame = new HashMap<>();
    public CustomUniforms custom;
    public int frameCounter;
    private long lastNanos;
    private final long startNanos = System.nanoTime();
    private final Matrix4f T1 = new Matrix4f(), T2 = new Matrix4f();
    private final Vector4f V1 = new Vector4f(), V2 = new Vector4f(), V3 = new Vector4f();
    private final Matrix4f prevModelView = new Matrix4f().zero(), prevProjection = new Matrix4f().zero();
    private final Vector3f prevCamera = new Vector3f();
    private float wetness, eyeSmoothBlock, eyeSmoothSky;
    public float sunPathRotation, shadowDistance = 160, shadowIntervalSize = 2;
    public float wetnessHalflife = 600, drynessHalflife = 200, eyeBrightnessHalflife = 10;
    public final Matrix4f modelView = new Matrix4f(), projection = new Matrix4f(), shadowModelView = new Matrix4f(), shadowProjection = new Matrix4f();
    /** per draw */
    public int entityId, blockEntityId, itemId, renderStage;

    public void configure(Map<String, String> consts) {
        sunPathRotation = f(consts.get("sunPathRotation"), 0);
        shadowDistance = f(consts.get("shadowDistance"), 160);
        shadowIntervalSize = f(consts.get("shadowIntervalSize"), 2);
        wetnessHalflife = f(consts.get("wetnessHalflife"), 600);
        drynessHalflife = f(consts.get("drynessHalflife"), 200);
        eyeBrightnessHalflife = f(consts.get("eyeBrightnessHalflife"), 10);
    }

    private static float f(String s, float def) {
        if (s == null) return def;
        try {
            return Float.parseFloat(s.replaceAll("[fF]$", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** at the start of the level render: frustumMatrix = the camera's rotation (gbufferModelView) */
    public void beginFrame(Camera camera, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9f;
        lastNanos = now;
        frameCounter = (frameCounter + 1) % 720720;
        prevModelView.set(modelView);
        prevProjection.set(projection);
        modelView.set(frustumMatrix);
        projection.set(projectionMatrix);
        Vec3 cam = camera.getPosition();
        Map<String, float[]> m = frame;
        put("gbufferModelView", modelView);
        put("gbufferModelViewInverse", T1.set(modelView).invert());
        put("gbufferProjection", projection);
        put("gbufferProjectionInverse", T1.set(projection).invert());
        put("gbufferPreviousModelView", prevModelView);
        put("gbufferPreviousProjection", prevProjection);
        set3("cameraPosition", (float) cam.x, (float) cam.y, (float) cam.z);
        set3("previousCameraPosition", prevCamera.x, prevCamera.y, prevCamera.z);
        set3("cameraPositionInt", (float) Math.floor(cam.x), (float) Math.floor(cam.y), (float) Math.floor(cam.z));
        set3("cameraPositionFract", (float) (cam.x - Math.floor(cam.x)), (float) (cam.y - Math.floor(cam.y)), (float) (cam.z - Math.floor(cam.z)));
        set3("previousCameraPositionInt", (float) Math.floor(prevCamera.x), (float) Math.floor(prevCamera.y), (float) Math.floor(prevCamera.z));
        set3("previousCameraPositionFract", prevCamera.x - (float) Math.floor(prevCamera.x), prevCamera.y - (float) Math.floor(prevCamera.y),
                prevCamera.z - (float) Math.floor(prevCamera.z));
        prevCamera.set((float) cam.x, (float) cam.y, (float) cam.z);
        set1("eyeAltitude", (float) cam.y);
        set1("viewWidth", width);
        set1("viewHeight", height);
        set1("aspectRatio", width / (float) height);
        set1("near", 0.05f);
        set1("far", mc.options.getEffectiveRenderDistance() * 16f);
        set1("frameCounter", frameCounter);
        set1("frameTime", dt);
        set1("frameTimeCounter", ((now - startNanos) / 1e9f) % 3600f);
        set1("screenBrightness", mc.options.gamma().get().floatValue());
        set1("hideGUI", mc.options.hideGui ? 1 : 0);
        set1("centerDepthSmooth", 1);
        Vector4f up = V1.set(0, 100, 0, 0).mul(modelView);
        set3("upPosition", up.x, up.y, up.z);
        if (level != null) {
            float skyAngle = level.getTimeOfDay(partialTick);
            float sunAngle = skyAngle < 0.75f ? skyAngle + 0.25f : skyAngle - 0.75f;
            float shadowAngle = sunAngle > 0.5f ? sunAngle - 0.5f : sunAngle;
            set1("sunAngle", sunAngle);
            set1("shadowAngle", shadowAngle);
            Matrix4f celestial = T2.set(modelView).rotateY((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(sunPathRotation))
                    .rotateX((float) Math.toRadians(skyAngle * 360f));
            Vector4f sun = V2.set(0, 100, 0, 0).mul(celestial), moon = V3.set(0, -100, 0, 0).mul(celestial);
            set3("sunPosition", sun.x, sun.y, sun.z);
            set3("moonPosition", moon.x, moon.y, moon.z);
            if (sunAngle <= 0.5f) set3("shadowLightPosition", sun.x, sun.y, sun.z);
            else set3("shadowLightPosition", moon.x, moon.y, moon.z);
            long dayTime = level.getDayTime();
            set1("worldTime", dayTime % 24000);
            set1("worldDay", dayTime / 24000);
            set1("moonPhase", level.getMoonPhase());
            float rain = level.getRainLevel(partialTick);
            set1("rainStrength", rain);
            set1("thunderStrength", level.getThunderLevel(partialTick));
            float hl = rain > wetness ? wetnessHalflife : drynessHalflife;
            wetness += (rain - wetness) * (float) (1 - Math.pow(0.5, dt * 20 / Math.max(1, hl)));
            set1("wetness", wetness);
            Vec3 sky = level.getSkyColor(cam, partialTick);
            set3("skyColor", (float) sky.x, (float) sky.y, (float) sky.z);
            float[] fog = com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();
            set3("fogColor", fog[0], fog[1], fog[2]);
            set1("fogStart", com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart());
            set1("fogEnd", com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd());
            set1("cloudHeight", level.effects().getCloudHeight());
            BlockPos eye = BlockPos.containing(cam);
            int block = level.getBrightness(LightLayer.BLOCK, eye), skyL = level.getBrightness(LightLayer.SKY, eye);
            set2("eyeBrightness", block * 16, skyL * 16);
            float k = (float) (1 - Math.pow(0.5, dt * 20 / Math.max(1, eyeBrightnessHalflife)));
            eyeSmoothBlock += (block * 16 - eyeSmoothBlock) * k;
            eyeSmoothSky += (skyL * 16 - eyeSmoothSky) * k;
            set2("eyeBrightnessSmooth", Math.round(eyeSmoothBlock), Math.round(eyeSmoothSky));
            var biome = level.getBiome(eye);
            var reg = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            set1("biome", reg.getId(biome.value()));
            var precip = biome.value().getPrecipitationAt(eye);
            set1("biome_precipitation", precip == net.minecraft.world.level.biome.Biome.Precipitation.RAIN ? 1
                    : precip == net.minecraft.world.level.biome.Biome.Precipitation.SNOW ? 2 : 0);
            set1("temperature", biome.value().getBaseTemperature());
            set1("rainfall", biome.value().getModifiedClimateSettings().downfall());
            set1("bedrockLevel", level.getMinBuildHeight());
            set1("heightLimit", level.getHeight());
            set1("hasSkylight", level.dimensionType().hasSkyLight() ? 1 : 0);
            set1("hasCeiling", level.dimensionType().hasCeiling() ? 1 : 0);
            if (biomeIds == null) {
                biomeIds = new HashMap<>();
                for (var e : reg.entrySet()) {
                    float[] c = new float[16];
                    java.util.Arrays.fill(c, reg.getId(e.getValue()));
                    biomeIds.put("BIOME_" + e.getKey().location().getPath().toUpperCase(), c);
                }
            }
        }
        LocalPlayer p = mc.player;
        if (p != null) {
            FogType ft = camera.getFluidInCamera();
            set1("isEyeInWater", ft == FogType.WATER ? 1 : ft == FogType.LAVA ? 2 : ft == FogType.POWDER_SNOW ? 3 : 0);
            set1("nightVision", p.hasEffect(MobEffects.NIGHT_VISION) ? 1 : 0);
            set1("blindness", p.hasEffect(MobEffects.BLINDNESS) ? 1 : 0);
            set1("darknessFactor", mc.options.darknessEffectScale().get().floatValue() * (p.hasEffect(MobEffects.DARKNESS) ? 1 : 0));
            set1("playerMood", p.getCurrentMood());
            set1("is_invisible", p.isInvisible() ? 1 : 0);
            set1("is_sneaking", p.isCrouching() ? 1 : 0);
            set1("is_sprinting", p.isSprinting() ? 1 : 0);
            set1("isElytraFlying", p.isFallFlying() ? 1 : 0);
            set1("isSpectator", p.isSpectator() ? 1 : 0);
            Vec3 look = p.getViewVector(partialTick);
            set3("playerLookVector", (float) look.x, (float) look.y, (float) look.z);
            Vec3 eyePos = p.getEyePosition(partialTick);
            set3("eyePosition", (float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
            set3("relativeEyePosition", (float) (cam.x - eyePos.x), (float) (cam.y - eyePos.y), (float) (cam.z - eyePos.z));
            set1("heldBlockLightValue", lightOf(p.getMainHandItem()));
            set1("heldBlockLightValue2", lightOf(p.getOffhandItem()));
            set1("firstPersonCamera", mc.options.getCameraType().isFirstPerson() ? 1 : 0);
        }
        shadowMatrices(cam, level == null ? 0 : slot("shadowAngle")[0]);
        put("shadowModelView", shadowModelView);
        put("shadowModelViewInverse", T1.set(shadowModelView).invert());
        put("shadowProjection", shadowProjection);
        put("shadowProjectionInverse", T1.set(shadowProjection).invert());
        if (custom != null) custom.evaluate(this::lookup, dt);
        frameStamp++;
    }

    private Map<String, float[]> biomeIds;

    /** a name for the custom-uniform expressions: frame values, biome constants */
    private float[] lookup(String name) {
        if (name.startsWith("BIOME_")) return biomeIds == null ? null : biomeIds.get(name);
        return slot(name);   // a standard name: its slot (filled now or later); unknown names stay a zero slot
    }

    private static float lightOf(net.minecraft.world.item.ItemStack s) {
        if (s.getItem() instanceof net.minecraft.world.item.BlockItem bi) return bi.getBlock().defaultBlockState().getLightEmission();
        return 0;
    }

    /** Iris 1.8.14's shadow matrices: orthographic box of shadowDistance, the sun's rotation, snapped to the interval */
    private void shadowMatrices(Vec3 cam, float shadowAngle) {
        float skyAngle = shadowAngle < 0.25f ? shadowAngle + 0.75f : shadowAngle - 0.25f;
        shadowModelView.identity().rotateX((float) Math.toRadians(90)).rotateZ((float) Math.toRadians(-skyAngle * 360f))
                .rotateX((float) Math.toRadians(sunPathRotation));
        if (shadowIntervalSize > 0) {
            float ix = (float) (cam.x % shadowIntervalSize) - shadowIntervalSize / 2, iy = (float) (cam.y % shadowIntervalSize) - shadowIntervalSize / 2,
                    iz = (float) (cam.z % shadowIntervalSize) - shadowIntervalSize / 2;
            shadowModelView.translate(ix, iy, iz);
        }
        shadowProjection.setOrthoSymmetric(2 * shadowDistance, 2 * shadowDistance, -256, 256);
    }

    private void put(String name, Matrix4f mat) {
        mat.get(slot(name));
    }

    /** a name's value slot: made once, written in place every frame (no array per value per frame) */
    public float[] slot(String name) {
        float[] v = frame.get(name);
        if (v == null) frame.put(name, v = new float[16]);
        return v;
    }

    private void set1(String name, float a) {
        slot(name)[0] = a;
    }

    private void set2(String name, float a, float b) {
        float[] v = slot(name);
        v[0] = a;
        v[1] = b;
    }

    private void set3(String name, float a, float b, float c) {
        float[] v = slot(name);
        v[0] = a;
        v[1] = b;
        v[2] = c;
    }

    // ------------------------------------------------------------------ upload

    long frameStamp;

    /** one program's active uniforms, matched once */
    public static final class Binding {
        final int program;
        final List<int[]> locs = new ArrayList<>();   // {location, glType, size}
        final List<String> names = new ArrayList<>();
        /** each uniform's value slot, resolved at the first upload (custom uniforms first, then the standard ones) */
        float[][] refs;
        int entityIdLoc = -1, blockEntityIdLoc = -1, itemIdLoc = -1, renderStageLoc = -1, normalMatLoc = -1, mvInvLoc = -1, projInvLoc = -1,
                lightmapMatLoc = -1, atlasSizeLoc = -1;
        long stamp = -1;

        Binding(int program) {
            this.program = program;
        }
    }

    /** reads a linked program's active uniforms; sets its sampler uniforms to the fixed units (program in use) */
    public static Binding bind(int program, Map<String, Integer> samplerUnits) {
        Binding b = new Binding(program);
        int n = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer size = st.mallocInt(1), type = st.mallocInt(1);
            for (int i = 0; i < n; i++) {
                String name = GL20.glGetActiveUniform(program, i, 256, size, type);
                if (name.endsWith("[0]")) name = name.substring(0, name.length() - 3);
                int loc = GL20.glGetUniformLocation(program, name);
                if (loc < 0) continue;
                int t = type.get(0);
                if (isSampler(t)) {
                    Integer unit = samplerUnits.get(name);
                    if (unit != null) GL20.glUniform1i(loc, unit);
                    continue;
                }
                switch (name) {
                    case "entityId" -> b.entityIdLoc = loc;
                    case "blockEntityId" -> b.blockEntityIdLoc = loc;
                    case "currentRenderedItemId" -> b.itemIdLoc = loc;
                    case "renderStage" -> b.renderStageLoc = loc;
                    case "sl_NormalMat" -> b.normalMatLoc = loc;
                    case "sl_ModelViewMatInverse" -> b.mvInvLoc = loc;
                    case "sl_ProjMatInverse" -> b.projInvLoc = loc;
                    case "sl_LightmapTextureMat" -> b.lightmapMatLoc = loc;
                    case "atlasSize" -> b.atlasSizeLoc = loc;
                    default -> {
                        b.locs.add(new int[] {loc, t, size.get(0)});
                        b.names.add(name);
                    }
                }
            }
        }
        return b;
    }

    static boolean isSampler(int t) {
        return t >= 0x8B5D && t <= 0x8B64 || t >= 0x8DC0 && t <= 0x8DD8 || t >= 0x900C && t <= 0x900F || t >= 0x9108 && t <= 0x910D;
    }

    /** frame values into the program (in use), once per frame */
    /** -Dsleep.shaders.stats: time and calls of the uploads */
    public static long frameUploadNanos, frameUploads, drawUploadNanos, drawUploads, applies;

    public void uploadFrame(Binding b) {
        if (b.stamp == frameStamp) return;
        b.stamp = frameStamp;
        long t0 = System.nanoTime();
        try {
            uploadFrame0(b);
        } finally {
            frameUploadNanos += System.nanoTime() - t0;
        }
    }

    private void uploadFrame0(Binding b) {
        float[][] refs = b.refs;
        if (refs == null) {
            refs = b.refs = new float[b.locs.size()][];
            for (int i = 0; i < refs.length; i++) {
                String name = b.names.get(i);
                float[] v = custom != null ? custom.values.get(name) : null;
                // only what this engine fills (the frame has run once by now): vanilla's own per-draw uniforms
                // (ModelViewMat, ProjMat, ChunkOffset...) are not ours to overwrite
                refs[i] = v != null ? v : frame.get(name);
            }
        }
        for (int i = 0; i < refs.length; i++) {
            float[] v = refs[i];
            if (v == null) continue;
            int[] l = b.locs.get(i);
            upload(l[0], l[1], v);
            frameUploads++;
        }
    }

    private static final Matrix4f TMP = new Matrix4f();
    private static final float[] MAT3 = new float[9];
    private static final org.joml.Matrix3f TMP3 = new org.joml.Matrix3f();
    /** vanilla's lightmap UV: (uv + 8) / 256 */
    private static final Matrix4f LIGHTMAP = new Matrix4f().scale(1 / 256f).translate(8, 8, 8);

    /** per draw: ids, render stage and the matrices derived from the current ModelViewMat / ProjMat */
    public void uploadDraw(Binding b, Matrix4f mv, Matrix4f proj) {
        long t0 = System.nanoTime();
        applies++;
        try {
            uploadDraw0(b, mv, proj);
        } finally {
            drawUploadNanos += System.nanoTime() - t0;
        }
    }

    private void uploadDraw0(Binding b, Matrix4f mv, Matrix4f proj) {
        if (b.entityIdLoc >= 0) GL20.glUniform1i(b.entityIdLoc, entityId);
        if (b.blockEntityIdLoc >= 0) GL20.glUniform1i(b.blockEntityIdLoc, blockEntityId);
        if (b.itemIdLoc >= 0) GL20.glUniform1i(b.itemIdLoc, itemId);
        if (b.renderStageLoc >= 0) GL20.glUniform1i(b.renderStageLoc, renderStage);
        try (MemoryStack st = MemoryStack.stackPush()) {
            FloatBuffer fb = st.mallocFloat(16);
            if (b.normalMatLoc >= 0) {
                TMP3.set(mv).invert().transpose().get(fb);
                GL20.glUniformMatrix3fv(b.normalMatLoc, false, fb.limit(9));
                fb.clear();
            }
            if (b.mvInvLoc >= 0) GL20.glUniformMatrix4fv(b.mvInvLoc, false, TMP.set(mv).invert().get(fb));
            if (b.projInvLoc >= 0) GL20.glUniformMatrix4fv(b.projInvLoc, false, TMP.set(proj).invert().get(fb));
            if (b.lightmapMatLoc >= 0) GL20.glUniformMatrix4fv(b.lightmapMatLoc, false, LIGHTMAP.get(fb));
        }
        if (b.atlasSizeLoc >= 0) {
            long wh = textureSize(com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0));
            org.lwjgl.opengl.GL20.glUniform2i(b.atlasSizeLoc, (int) (wh >>> 32), (int) wh);
        }
    }

    /**
     * A texture's size (width << 32 | height), asked of GL once per texture (a bind and two queries per draw cost ~25 us
     * each - 1 ms a frame on client_entities); forgotten when the texture is deleted (its id may come back resized).
     */
    private static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap SIZES = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    static long textureSize(int tex) {
        if (tex == 0) return 0;
        long wh = SIZES.getOrDefault(tex, -1L);
        if (wh != -1L) return wh;
        int w = org.lwjgl.opengl.GL45.glGetTextureLevelParameteri(tex, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
        int h = org.lwjgl.opengl.GL45.glGetTextureLevelParameteri(tex, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
        wh = (long) w << 32 | (h & 0xFFFFFFFFL);
        SIZES.put(tex, wh);
        return wh;
    }

    /** GlStateManager deletes a texture */
    public static void forgetTexture(int tex) {
        SIZES.remove(tex);
    }

    static void upload(int loc, int type, float[] v) {
        switch (type) {
            case 0x1406 -> GL20.glUniform1f(loc, v[0]);   // FLOAT
            case 0x8B50 -> GL20.glUniform2f(loc, v[0], at(v, 1));
            case 0x8B51 -> GL20.glUniform3f(loc, v[0], at(v, 1), at(v, 2));
            case 0x8B52 -> GL20.glUniform4f(loc, v[0], at(v, 1), at(v, 2), at(v, 3));
            case 0x1404, 0x8B56 -> GL20.glUniform1i(loc, (int) v[0]);   // INT, BOOL
            case 0x8B53, 0x8B57 -> GL20.glUniform2i(loc, (int) v[0], (int) at(v, 1));
            case 0x8B54, 0x8B58 -> GL20.glUniform3i(loc, (int) v[0], (int) at(v, 1), (int) at(v, 2));
            case 0x8B55, 0x8B59 -> GL20.glUniform4i(loc, (int) v[0], (int) at(v, 1), (int) at(v, 2), (int) at(v, 3));
            case 0x8B5C -> {   // MAT4
                if (v.length >= 16) GL20.glUniformMatrix4fv(loc, false, v);
            }
            case 0x8B5B -> {   // MAT3
                if (v.length >= 16) {
                    float[] m3 = MAT3;
                    m3[0] = v[0];
                    m3[1] = v[1];
                    m3[2] = v[2];
                    m3[3] = v[4];
                    m3[4] = v[5];
                    m3[5] = v[6];
                    m3[6] = v[8];
                    m3[7] = v[9];
                    m3[8] = v[10];
                    GL20.glUniformMatrix3fv(loc, false, m3);
                }
                else if (v.length >= 9) GL20.glUniformMatrix3fv(loc, false, v);
            }
            default -> {}
        }
    }

    private static float at(float[] v, int i) {
        return i < v.length ? v[i] : 0;
    }
}
