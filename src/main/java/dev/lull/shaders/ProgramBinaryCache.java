package dev.lull.shaders;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL41;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Linked shaderpack programs kept on disk (glGetProgramBinary / glProgramBinary): a pack's ~100 programs compile once
 * per driver; later starts load them instead of compiling (Intel UHD, Complementary: 57 s of compile + link at the
 * level's first frame, 11-02). The key is a hash of the driver (vendor, renderer, version) and everything the
 * program is made of (its sources, the attribute names bound); a binary the driver no longer takes (a driver update)
 * is dropped and the program compiled again. Files: <game>/sleepium/shader-cache (dev runs: sleep/), written off the
 * render thread. Off by default (-Dsleep.shaders.binaryCache=true): the drivers keep their own cache, and ours did
 * not beat it (11-02, Complementary's 101 programs: NVIDIA 1.2 s from the driver's cache vs 2.5 s loaded from ours;
 * Intel UHD 6.1 s vs 5.3 s; filling ours made the first start longer, 9.6 / 10.0 s). The first compile on a driver
 * is what is long (Intel 57 s): ShaderPipeline prepares a pack over frames instead.
 */
public final class ProgramBinaryCache {
    private ProgramBinaryCache() {}

    private static final int RETRIEVABLE_HINT = 0x8257;
    private static Boolean on;
    private static String driver;
    private static Path dir;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sleep-shader-cache");
        t.setDaemon(true);
        return t;
    });
    public static int hits, misses, rejected, stored;

    public static boolean on() {
        Boolean o = on;
        if (o == null) {
            var caps = GL.getCapabilities();
            o = (caps.OpenGL41 || caps.GL_ARB_get_program_binary) && GL11.glGetInteger(GL41.GL_NUM_PROGRAM_BINARY_FORMATS) > 0
                    && Boolean.getBoolean("sleep.shaders.binaryCache");
            if (o) {
                driver = GL11.glGetString(GL11.GL_VENDOR) + "|" + GL11.glGetString(GL11.GL_RENDERER) + "|" + GL11.glGetString(GL11.GL_VERSION);
                dir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve(dev.lull.SleepOut.ROOT).resolve("shader-cache");
            }
            on = o;
        }
        return o;
    }

    /** the cache key of a program made of these parts (with the driver) */
    public static String key(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(driver.getBytes(StandardCharsets.UTF_8));
            for (String p : parts) {
                md.update((byte) 0);
                md.update(p.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest(), 0, 20);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path file(String key) {
        return dir.resolve(key + ".bin");
    }

    public static boolean has(String key) {
        return Files.exists(file(key));
    }

    /** the program's executable from the cache; false: none, or the driver refused it (then it is dropped) */
    public static boolean load(int program, String key) {
        Path f = file(key);
        byte[] data;
        try {
            data = Files.readAllBytes(f);
        } catch (IOException e) {
            misses++;
            return false;
        }
        if (data.length <= 4) return reject(f);
        ByteBuffer b = MemoryUtil.memAlloc(data.length - 4);
        try {
            b.put(data, 4, data.length - 4).flip();
            int format = ByteBuffer.wrap(data, 0, 4).getInt();
            GL41.glProgramBinary(program, format, b);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) return reject(f);
        } finally {
            MemoryUtil.memFree(b);
        }
        hits++;
        return true;
    }

    private static boolean reject(Path f) {
        rejected++;
        try {
            Files.deleteIfExists(f);
        } catch (IOException ignored) {
        }
        return false;
    }

    /** before glLinkProgram: some drivers keep a retrievable binary only when asked */
    public static void retrievable(int program) {
        GL41.glProgramParameteri(program, RETRIEVABLE_HINT, GL11.GL_TRUE);
    }

    /** a linked program into the cache (the binary read here, written by a background thread) */
    public static void store(int program, String key) {
        misses++;
        int len = GL20.glGetProgrami(program, GL41.GL_PROGRAM_BINARY_LENGTH);
        if (len <= 0) return;
        byte[] out = new byte[len + 4];
        ByteBuffer b = MemoryUtil.memAlloc(len);
        try (MemoryStack st = MemoryStack.stackPush()) {
            var length = st.mallocInt(1);
            var format = st.mallocInt(1);
            GL41.glGetProgramBinary(program, length, format, b);
            int n = length.get(0);
            if (n <= 0) return;
            ByteBuffer.wrap(out, 0, 4).putInt(format.get(0));
            b.get(out, 4, n);
            byte[] data = n == len ? out : java.util.Arrays.copyOf(out, n + 4);
            Path f = file(key);
            WRITER.execute(() -> {
                try {
                    Files.createDirectories(dir);
                    Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                    Files.write(tmp, data);
                    Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    stored++;
                } catch (IOException e) {
                    System.out.println("[sleep/shaders] shader cache: " + e);
                }
            });
        } finally {
            MemoryUtil.memFree(b);
        }
    }
}
