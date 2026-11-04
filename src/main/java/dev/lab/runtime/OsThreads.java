package dev.lab.runtime;

/**
 * OS-level thread placement. Windows through JNA when it is on the classpath (Minecraft ships it), otherwise
 * Java thread priorities only (HotSpot on Windows maps them to OS priorities).
 */
public final class OsThreads {
    public static final int BELOW_NORMAL = -1, NORMAL = 0, ABOVE_NORMAL = 1;

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private static final Object K32 = loadKernel32();

    private OsThreads() {}

    public interface Kernel32 extends com.sun.jna.Library {
        com.sun.jna.Pointer GetCurrentThread();
        boolean SetThreadPriority(com.sun.jna.Pointer thread, int priority);
        long SetThreadAffinityMask(com.sun.jna.Pointer thread, long mask);
        int GetCurrentProcessorNumber();
    }

    private static Object loadKernel32() {
        if (!WINDOWS || Boolean.getBoolean("lab.runtime.noJna")) return null;
        try {
            return com.sun.jna.Native.load("kernel32", Kernel32.class);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean nativeAvailable() {
        return K32 != null;
    }

    /** Call from the thread itself. */
    public static void setCurrentPriority(int osPriority) {
        if (K32 != null) {
            Kernel32 k = (Kernel32) K32;
            k.SetThreadPriority(k.GetCurrentThread(), osPriority);
        } else {
            Thread.currentThread().setPriority(switch (osPriority) {
                case BELOW_NORMAL -> Thread.NORM_PRIORITY - 1;
                case ABOVE_NORMAL -> Thread.NORM_PRIORITY + 1;
                default -> Thread.NORM_PRIORITY;
            });
        }
    }

    /** Call from the thread itself. mask 0 = leave as is. Returns false if not supported. */
    public static boolean pinCurrent(long affinityMask) {
        if (affinityMask == 0 || K32 == null) return false;
        Kernel32 k = (Kernel32) K32;
        return k.SetThreadAffinityMask(k.GetCurrentThread(), affinityMask) != 0;
    }

    public static int currentCpu() {
        return K32 != null ? ((Kernel32) K32).GetCurrentProcessorNumber() : -1;
    }
}
