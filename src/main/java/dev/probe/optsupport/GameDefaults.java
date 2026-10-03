package dev.probe.optsupport;

/**
 * The engine's optimisations on by default in a built jar (10-04: a friend's launcher sets no -D flags, and the game must
 * run on the engine as it does here). In production - or with {@code -Dlab.opt.defaults=true} - every flag of
 * {@code probe/game_opts.txt} (the jar's copy of tools/game_opts.txt, the set the game is tested with) is set unless
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
        String opt = System.getProperty("lab.opt.defaults");
        if ("false".equals(opt)) return;
        boolean prod;
        try {
            prod = net.neoforged.fml.loading.FMLLoader.isProduction();
        } catch (Throwable t) {
            prod = false;
        }
        if (!prod && !"true".equals(opt)) return;
        try (var in = GameDefaults.class.getResourceAsStream("/probe/game_opts.txt")) {
            if (in == null) {
                System.out.println("[probe/opt] defaults: probe/game_opts.txt missing from the jar");
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
            System.out.println("[probe/opt] defaults (" + (prod ? "production" : "lab.opt.defaults") + "): " + set + " flags on, " + kept + " given");
        } catch (java.io.IOException e) {
            System.out.println("[probe/opt] defaults: " + e);
        }
    }
}
