package testmods.encrypt;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test only (-Dsleep.test.encrypt=true on the client): no session call before the key exchange - an offline test
 * account cannot make it, and 1.20.1's hello cannot say "don't authenticate". The key exchange and the ciphers are the
 * real ones. See EncryptOfflineMixin.
 */
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class EncryptClientMixin {
    @Inject(method = "authenticateServer", at = @At("HEAD"), cancellable = true)
    private void sleep$noSessionCall(String serverHash, CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(null);
    }
}
