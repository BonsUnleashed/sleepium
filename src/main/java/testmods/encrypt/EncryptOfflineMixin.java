package testmods.encrypt;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Test only (-Dsleep.test.encrypt=true on the server): the login is encrypted as on an online-mode server, without
 * Mojang's session servers - test accounts are offline. The server takes the encrypted path, tells the client not to
 * authenticate (ClientboundHelloPacket.shouldAuthenticate = false) and, once the key is exchanged, verifies the offline
 * profile instead of asking the session server. The connection's ciphers are the real ones: a client handler in front
 * of the decryption (Sleepium's coalescing decoder up to 1.1.4: logins hung on "Encrypting...") shows here.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class EncryptOfflineMixin {
    @Shadow @Final MinecraftServer server;
    @Shadow @Final private byte[] challenge;
    @Shadow String requestedUsername;

    @Shadow
    abstract void startClientVerification(GameProfile profile);

    @Redirect(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
    private boolean sleep$encrypted(MinecraftServer server) {
        return true;
    }

    @Redirect(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void sleep$noSessionServer(Connection connection, Packet<?> packet) {
        connection.send(packet instanceof ClientboundHelloPacket
                ? new ClientboundHelloPacket("", this.server.getKeyPair().getPublic().getEncoded(), this.challenge, false) : packet);
    }

    @Redirect(method = "handleKey", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V"))
    private void sleep$offlineProfile(Thread authenticator) {
        this.startClientVerification(UUIDUtil.createOfflineProfile(this.requestedUsername));
    }
}
