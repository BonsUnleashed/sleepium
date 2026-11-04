package dev.lull;

import net.minecraftforge.fml.common.Mod;

/** Sleepium on Minecraft 1.20.1 / Forge: the port's entry point (the engine comes over from ../sleep step by step). */
@Mod(SleepMod.MOD_ID)
public final class SleepMod {
    public static final String MOD_ID = "sleepium";

    public SleepMod() {
        System.out.println("[sleep] Sleepium for 1.20.1 / Forge loaded");
    }
}
