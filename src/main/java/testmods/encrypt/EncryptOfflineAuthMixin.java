package testmods.encrypt;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import java.net.InetAddress;
import net.minecraft.core.UUIDUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Test only (-Dsleep.test.encrypt=true on the server): the login's authenticator thread (ServerLoginPacketListenerImpl's
 * anonymous Thread) takes the offline profile of the name instead of asking Mojang's session server; vanilla then goes
 * on as after a successful check. See EncryptOfflineMixin.
 */
@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1", remap = false)
public abstract class EncryptOfflineAuthMixin {
    @Redirect(method = "run", at = @At(value = "INVOKE",
            target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;hasJoinedServer(Lcom/mojang/authlib/GameProfile;Ljava/lang/String;Ljava/net/InetAddress;)Lcom/mojang/authlib/GameProfile;"))
    private GameProfile sleep$offlineProfile(MinecraftSessionService service, GameProfile user, String serverId, InetAddress address) {
        return new GameProfile(UUIDUtil.createOfflinePlayerUUID(user.getName()), user.getName());
    }
}
