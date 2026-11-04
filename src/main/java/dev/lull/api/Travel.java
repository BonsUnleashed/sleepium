package dev.lull.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * A teleport that does not stop the server. Vanilla's teleport (and NeoForge's Entity.setPosRaw patch, which loads
 * the new position's chunk) makes the server thread wait while the destination is loaded or generated: /tp into
 * fresh terrain froze the server 0.35-1 s (10-08). Here the destination's chunks are asked for first - a ticket of
 * radius {@code RADIUS} around it, loaded and generated on the workers like any chunk - and the player is moved with
 * vanilla's teleportTo once they are all there, so nothing has to wait. If they are not ready within
 * {@code TIMEOUT_TICKS} the teleport happens anyway, as vanilla's would. Server thread only.
 *
 * <p>For a game's travel (fast travel points, warps, a summoned mount): {@code Travel.teleportWhenReady(player, level,
 * x, y, z, yaw, pitch, done)}; {@code done} gets true if it waited for nothing, false after a timeout or if the player
 * left. The player stays where they are until then (show a fade or a short animation meanwhile).
 */
public final class Travel {
    /** chunks around the destination made FULL before the move: 2 → 5 x 5 */
    public static final int RADIUS = 2;
    public static final int TIMEOUT_TICKS = 30 * 20;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("probe_travel", Comparator.comparingLong(ChunkPos::toLong), TIMEOUT_TICKS + 40);
    private static final List<Pending> PENDING = new ArrayList<>();

    private Travel() {}

    private record Pending(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch,
                           @Nullable Consumer<Boolean> done, ChunkPos center, long since) {}

    /** server thread: move {@code player} to (x, y, z) in {@code level} once the chunks there are loaded */
    public static void teleportWhenReady(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch,
                                         @Nullable Consumer<Boolean> done) {
        ChunkPos c = new ChunkPos(net.minecraft.core.BlockPos.containing(x, y, z));
        level.getChunkSource().addRegionTicket(TICKET, c, RADIUS, c);
        PENDING.add(new Pending(player, level, x, y, z, yaw, pitch, done, c, level.getGameTime()));
    }

    /** server thread, after each tick (SleepMod): the requests whose chunks are ready, or which waited too long, go */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        for (int i = 0; i < PENDING.size(); ) {
            Pending p = PENDING.get(i);
            boolean gone = p.player.isRemoved() || p.player.hasDisconnected();
            boolean ready = !gone && ready(p);
            boolean late = p.level.getGameTime() - p.since > TIMEOUT_TICKS;
            if (!gone && !ready && !late) {
                i++;
                continue;
            }
            PENDING.remove(i);
            if (!gone) p.player.teleportTo(p.level, p.x, p.y, p.z, p.yaw, p.pitch);
            p.level.getChunkSource().removeRegionTicket(TICKET, p.center, RADIUS, p.center);   // vanilla's own teleport ticket keeps it a while
            if (p.done != null) p.done.accept(ready);
        }
    }

    private static boolean ready(Pending p) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
                if (p.level.getChunkSource().getChunkNow(p.center.x + dx, p.center.z + dz) == null) return false;   // loaded FULL (hasChunk only tells the ticket level)
        return true;
    }
}
