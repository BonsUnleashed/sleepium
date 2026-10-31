package dev.sleep.mixin;

import dev.sleep.optsupport.sleep.SleepEngine;
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
    private Predicate<?> sleep$players(Predicate<?> p) {
        SleepEngine.unobservedRead();
        return p;
    }

    @ModifyVariable(method = "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), argsOnly = true)
    private UUID sleep$entityByUuid(UUID id) {
        SleepEngine.unobservedRead();
        return id;
    }

    @ModifyVariable(method = "getEntity(I)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), argsOnly = true)
    private int sleep$entityById(int id) {
        SleepEngine.unobservedRead();
        return id;
    }

    @ModifyReturnValue(method = "players", at = @At("RETURN"))
    private List<ServerPlayer> sleep$playerList(List<ServerPlayer> v) {
        SleepEngine.playersRead((ServerLevel) (Object) this, v);
        return v;
    }

    @ModifyReturnValue(method = "getRandomPlayer", at = @At("RETURN"))
    private ServerPlayer sleep$randomPlayer(ServerPlayer v) {
        SleepEngine.unobservedRead();
        return v;
    }
}
