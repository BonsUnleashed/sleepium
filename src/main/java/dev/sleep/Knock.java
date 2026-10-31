package dev.sleep;

import java.util.Set;

/**
 * Measurement only: -Dprobe.knock=sensors,start,run,forget,travel,push,goals switches parts of a mob's tick off
 * (Knock*Mixin), to see how much of the tick each part holds. Behaviour changes with them (a mob that stops walking
 * also stops paying for collisions): upper bounds for what making a part cheaper could win, never a setting to play.
 */
public final class Knock {
    private static final Set<String> K = Set.of(System.getProperty("probe.knock", "").split(","));
    public static final boolean SENSORS = K.contains("sensors"), START = K.contains("start"), RUN = K.contains("run"),
            FORGET = K.contains("forget"), TRAVEL = K.contains("travel"), PUSH = K.contains("push"), GOALS = K.contains("goals");

    private Knock() {}
}
