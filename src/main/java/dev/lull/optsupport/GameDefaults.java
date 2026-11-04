package dev.lull.optsupport;

/**
 * The engine's optimisations on by default in a built jar (10-04: a friend's launcher sets no -D flags, and the game must
 * run on the engine as it does here). In production - or with {@code -Dlab.opt.defaults=true} - every flag of
 * {@code sleep/defaults.txt} (the jar's copy of sleep/defaults.txt, the set the engine is tested with) is set unless
 * already given: {@code -Dlab.opt.X=false} still turns one off, {@code -Dlab.opt.defaults=false} all of them. Dev runs
 * are untouched (their flags come from run-client-direct.sh; OPT=0 stays the plain engine for A/B).
 * <p>
 * Called first by every mixin config plugin (whichever Mixin loads first), before any flag is read.
 */
public final class GameDefaults {
    private GameDefaults() {}

    private static boolean done;

    public static synchronized void ensure() {
        if (done) return;
        done = true;
        SleepConfig.load();   // the player's settings first: they win over the defaults
        try {
            defaults();
        } finally {
            SleepConfig.started();
        }
    }

    private static void defaults() {
        String opt = System.getProperty("lab.opt.defaults");
        if ("false".equals(opt)) return;
        boolean prod;
        try {
            prod = net.minecraftforge.fml.loading.FMLLoader.isProduction();
        } catch (Throwable t) {
            prod = false;
        }
        if (!prod && !"true".equals(opt)) return;
        try (var in = GameDefaults.class.getResourceAsStream("/sleepium/defaults.txt")) {
            if (in == null) {
                System.out.println("[sleep/opt] defaults: sleep/defaults.txt missing from the jar");
                return;
            }
            int set = 0, kept = 0;
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\\R")) {
                line = line.trim();
                if (!line.startsWith("-D") || line.indexOf('=') < 0) continue;
                String key = line.substring(2, line.indexOf('=')), value = line.substring(line.indexOf('=') + 1);
                if (System.getProperty(key) != null) {
                    kept++;
                    continue;
                }
                System.setProperty(key, value);
                set++;
            }
            System.out.println("[sleep/opt] defaults (" + (prod ? "production" : "lab.opt.defaults") + "): " + set + " flags on, " + kept + " given");
        } catch (java.io.IOException e) {
            System.out.println("[sleep/opt] defaults: " + e);
        }
    }
}
