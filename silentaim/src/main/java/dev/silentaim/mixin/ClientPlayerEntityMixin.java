package dev.silentaim.mixin;

import dev.silentaim.SilentAimClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects the silent rotation only while the movement packets are built, so the client camera
 * is left exactly where the player is looking.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void silentaim$applySilentRotation(CallbackInfo info) {
        SilentAimClient.aim().pushSilentRotation((ClientPlayerEntity) (Object) this, SilentAimClient.config());
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void silentaim$restoreSilentRotation(CallbackInfo info) {
        SilentAimClient.aim().popSilentRotation((ClientPlayerEntity) (Object) this);
    }
}
