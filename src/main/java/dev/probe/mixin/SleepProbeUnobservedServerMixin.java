package dev.probe.mixin;

import dev.probe.optsupport.sleep.SleepEngine;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Sleep engine: player and entity-by-id lookups (see SleepProbeUnobservedMixin). */
@Mixin(ServerLevel.class)
public abstract class SleepProbeUnobservedServerMixin {
    @ModifyVariable(method = "getPlayers(Ljava/util/function/Predicate;I)Ljava/util/List;", at = @At("HEAD"), argsOnly = true)
    private Predicate<?> probe$players(Predicate<?> p) {
        SleepEngine.unobservedRead();
        return p;
    }

    @ModifyVariable(method = "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), argsOnly = true)
    private UUID probe$entityByUuid(UUID id) {
        SleepEngine.unobservedRead();
        return id;
    }

    @ModifyVariable(method = "getEntity(I)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), argsOnly = true)
    private int probe$entityById(int id) {
        SleepEngine.unobservedRead();
        return id;
    }

    @ModifyReturnValue(method = "players", at = @At("RETURN"))
    private List<ServerPlayer> probe$playerList(List<ServerPlayer> v) {
        SleepEngine.unobservedRead();
        return v;
    }

    @ModifyReturnValue(method = "getRandomPlayer", at = @At("RETURN"))
    private ServerPlayer probe$randomPlayer(ServerPlayer v) {
        SleepEngine.unobservedRead();
        return v;
    }
}
