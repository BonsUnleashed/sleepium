package testmods.encrypt;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Test only (-Dsleep.test.encrypt=true on the server): the login is encrypted as on an online-mode server, test
 * accounts offline - 1.21.1's testmods.encrypt for 1.20.1. The server takes the encrypted path (here), its authenticator
 * takes the offline profile instead of asking Mojang's session server (EncryptOfflineAuthMixin), and the client skips
 * its own session call (EncryptClientMixin; 1.20.1's hello has no "don't authenticate" flag). The ciphers are real.
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class EncryptOfflineMixin {
    @Redirect(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
    private boolean sleep$encrypted(MinecraftServer server) {
        return true;
    }
}
