package dev.silentaim.mixin;

import dev.silentaim.SilentAimClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the aim right before the attack is built, so the hit matches the rotation the
 * server has been given.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void silentaim$beforeAttack(CallbackInfoReturnable<Boolean> info) {
        SilentAimClient.aim().onAttack((MinecraftClient) (Object) this, SilentAimClient.config());
    }
}
