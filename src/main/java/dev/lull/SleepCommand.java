package dev.lull;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * /sleep on 1.20.1: what the test tools use so far - reseed and the region hash (tools/exactness.py) - and, in dev
 * runs or with -Dsleep.tickCommand=true, the test tools' /tick (TickControl). More of the 1.21.1 engine's subcommands
 * come over with the features they report on.
 */
public final class SleepCommand {
    private SleepCommand() {}

    /** /tick on 1.20.1 is ours: only in dev runs (the tools) or when asked */
    private static final boolean TICK = !net.minecraftforge.fml.loading.FMLEnvironment.production || Boolean.getBoolean("sleep.tickCommand");

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("sleep").requires(s -> s.hasPermission(2))
                .then(Commands.literal("audit").executes(ctx -> {
                    // every mixin applied now (Mixin loads each target): apply failures show at once, not when a class first loads
                    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
                    say(ctx, "mixin audit done");
                    return 1;
                }))
                .then(Commands.literal("reseed").executes(ctx -> {
                    // exactness tests: vanilla's clock-seeded randoms to fixed seeds, right before a scene is built
                    TestSeeds.reseed(ctx.getSource().getServer());
                    say(ctx, "randoms reseeded");
                    return 1;
                }))
                .then(Commands.literal("hash")
                        .then(Commands.argument("x1", IntegerArgumentType.integer()).then(Commands.argument("y1", IntegerArgumentType.integer())
                        .then(Commands.argument("z1", IntegerArgumentType.integer()).then(Commands.argument("x2", IntegerArgumentType.integer())
                        .then(Commands.argument("y2", IntegerArgumentType.integer()).then(Commands.argument("z2", IntegerArgumentType.integer()).executes(ctx -> {
                            String r = regionHash(ctx.getSource().getLevel(), IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "y1"),
                                    IntegerArgumentType.getInteger(ctx, "z1"), IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "y2"),
                                    IntegerArgumentType.getInteger(ctx, "z2"));
                            System.out.println("[sleep] " + r);
                            say(ctx, r);
                            return 1;
                        })))))))));
        if (TICK) d.register(Commands.literal("tick").requires(s -> s.hasPermission(3))
                .then(Commands.literal("freeze").executes(ctx -> {
                    TickControl.freeze(true);
                    say(ctx, "The game is frozen");
                    return 1;
                }))
                .then(Commands.literal("unfreeze").executes(ctx -> {
                    TickControl.freeze(false);
                    say(ctx, "The game is running normally");
                    return 1;
                }))
                .then(Commands.literal("step").then(Commands.argument("time", IntegerArgumentType.integer(1)).executes(ctx -> {
                    int n = IntegerArgumentType.getInteger(ctx, "time");
                    TickControl.step(n);
                    say(ctx, "Stepping " + n + " tick(s)");
                    return 1;
                })))
                .then(Commands.literal("query").executes(ctx -> {
                    for (String line : TickControl.query(ctx.getSource().getServer())) say(ctx, line);
                    return 1;
                })));
    }

    /**
     * The end of every server tick (TickTimer, LOWEST): the benches' clocks. On 1.21.1 also GenBench (world generation),
     * NetBench (networking) - not ported yet - and the auto-stop of /sleep run (its report commands come over with the
     * event counter).
     */
    static void onTickEnd(net.minecraft.server.MinecraftServer server) {
        IdleBench.onTickEnd(server);
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
    }

    static String canonNbt(net.minecraft.nbt.Tag t) {
        if (t instanceof net.minecraft.nbt.CompoundTag c) {
            StringBuilder sb = new StringBuilder("{");
            for (String k : new java.util.TreeSet<>(c.getAllKeys())) sb.append(k).append(':').append(canonNbt(c.get(k))).append(',');
            return sb.append('}').toString();
        }
        if (t instanceof net.minecraft.nbt.ListTag l && !l.isEmpty() && l.get(0) instanceof net.minecraft.nbt.CompoundTag) {
            java.util.List<String> els = new java.util.ArrayList<>();
            for (net.minecraft.nbt.Tag x : l) els.add(canonNbt(x));
            java.util.Collections.sort(els);
            return els.toString();
        }
        if (t instanceof net.minecraft.nbt.ListTag l) {
            StringBuilder sb = new StringBuilder("[");
            for (net.minecraft.nbt.Tag x : l) sb.append(canonNbt(x)).append(',');
            return sb.append(']').toString();
        }
        return String.valueOf(t);
    }

    private static final java.util.List<String> BE_ROWS = System.getProperty("sleep.hashBeRows") != null ? new java.util.ArrayList<>() : null;

    /** The region's blocks, block entities (saved and client NBT) and, with -Dsleep.hashEntities, its entities. */
    static String regionHash(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2) {
        long h = 1125899906842597L;
        int bes = 0;
        var p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    p.set(x, y, z);
                    h = h * 31 + level.getBlockState(p).toString().hashCode();
                    var be = level.getBlockEntity(p);
                    if (be != null) {
                        bes++;
                        String nbt = be.saveWithoutMetadata().toString();
                        h = h * 31 + nbt.hashCode();
                        String client = Boolean.getBoolean("sleep.hashClientNbt") ? String.valueOf(be.getUpdateTag()) : null;
                        if (client != null) h = h * 31 + client.hashCode();
                        if (BE_ROWS != null) BE_ROWS.add(p.toShortString() + " " + level.getBlockState(p) + " " + nbt + (client != null ? " client=" + client : ""));
                    }
                }
        if (BE_ROWS != null) {
            try {
                java.nio.file.Files.write(java.nio.file.Path.of(System.getProperty("sleep.hashBeRows")), BE_ROWS);
            } catch (java.io.IOException e) {
                System.out.println("[sleep/hash] block entity rows not written: " + e);
            }
            BE_ROWS.clear();
        }
        int ents = 0;
        if (Boolean.getBoolean("sleep.hashEntities")) {
            var box = new net.minecraft.world.phys.AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, Math.max(z1, z2) + 1);
            java.util.List<String> rows = new java.util.ArrayList<>();
            for (var e : level.getEntities((net.minecraft.world.entity.Entity) null, box, x -> !(x instanceof net.minecraft.world.entity.player.Player))) {
                var tag = new net.minecraft.nbt.CompoundTag();
                e.saveWithoutId(tag);
                tag.remove("UUID");
                rows.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()) + " " + Double.doubleToLongBits(e.getX()) + " "
                        + Double.doubleToLongBits(e.getY()) + " " + Double.doubleToLongBits(e.getZ()) + " " + e.getDeltaMovement() + " " + canonNbt(tag)
                        + (Boolean.getBoolean("sleep.hashEntitiesDeep") ? " deep=" + Long.toHexString(DeepHash.of(e)) : ""));
            }
            java.util.Collections.sort(rows);
            String rowsFile = System.getProperty("sleep.hashRows");
            if (rowsFile != null) {
                try {
                    java.nio.file.Files.write(java.nio.file.Path.of(rowsFile), rows);
                } catch (java.io.IOException ex) {
                    System.out.println("[sleep/hash] rows not written: " + ex);
                }
            }
            for (String r : rows) h = h * 31 + r.hashCode();
            ents = rows.size();
        }
        return String.format(Locale.ROOT, "region hash %016x (block entities %d, entities %d, game time %d)", h, bes, ents, level.getGameTime());
    }
}
