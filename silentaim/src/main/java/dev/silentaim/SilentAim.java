package dev.silentaim;

import dev.silentaim.aim.RotationMath;
import dev.silentaim.aim.TargetSelector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds the current target and the "server side" rotation that gets injected while the
 * player's own camera stays untouched.
 */
public final class SilentAim {

    private LivingEntity target;
    private boolean active;
    private boolean silentRotationReady;

    private boolean rotationPushed;
    private float savedYaw;
    private float savedPitch;

    private float silentYaw;
    private float silentPitch;

    /**
     * Runs once per client tick and refreshes the target and the desired rotation.
     */
    public void tick(MinecraftClient client, SilentAimConfig config) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (config == null || !config.enabled || player == null || world == null) {
            clear(player);
            return;
        }

        LivingEntity found = TargetSelector.select(client, config);
        if (found == null) {
            clear(player);
            return;
        }

        target = found;
        active = true;

        float[] wanted = RotationMath.lookAt(player.getEyePos(), TargetSelector.aimPoint(found));

        if (config.randomize && config.randomJitter > 0.0) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            wanted[0] = MathHelper.wrapDegrees(wanted[0] + (float) random.nextDouble(-config.randomJitter, config.randomJitter));
            wanted[1] = MathHelper.clamp(wanted[1] + (float) random.nextDouble(-config.randomJitter, config.randomJitter), -90.0F, 90.0F);
        }

        if (!silentRotationReady) {
            // First tick of a lock-on: start from where the server thinks we are looking.
            silentYaw = player.getYaw();
            silentPitch = player.getPitch();
            silentRotationReady = true;
        }

        float maxStep = (float) config.speed;
        silentYaw = RotationMath.step(silentYaw, wanted[0], maxStep);
        silentPitch = RotationMath.step(silentPitch, wanted[1], maxStep);

        if (!config.silent) {
            // Visible aim assist: move the real camera as well.
            player.setYaw(silentYaw);
            player.setPitch(silentPitch);
        }
    }

    /**
     * Called at the head of {@code ClientPlayerEntity#sendMovementPackets} so the outgoing
     * look packet carries the silent rotation.
     */
    public void pushSilentRotation(ClientPlayerEntity player, SilentAimConfig config) {
        if (config == null || !config.silent || !active || !silentRotationReady) {
            return;
        }

        savedYaw = player.getYaw();
        savedPitch = player.getPitch();
        player.setYaw(silentYaw);
        player.setPitch(silentPitch);
        rotationPushed = true;
    }

    /**
     * Called at the tail of {@code ClientPlayerEntity#sendMovementPackets} to put the real
     * rotation back so rendering and hitbox logic stay untouched.
     */
    public void popSilentRotation(ClientPlayerEntity player) {
        if (!rotationPushed) {
            return;
        }

        player.setYaw(savedYaw);
        player.setPitch(savedPitch);
        rotationPushed = false;
    }

    /**
     * Called right before {@code MinecraftClient#doAttack} so the attack always lands on the
     * silent rotation instead of the one currently on screen.
     */
    public void onAttack(MinecraftClient client, SilentAimConfig config) {
        ClientPlayerEntity player = client.player;
        if (config == null || !config.silent || player == null) {
            return;
        }
        if (!active || !silentRotationReady || target == null || !target.isAlive() || target.isRemoved()) {
            return;
        }

        // Push the silent rotation immediately so the server validates against it.
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                    silentYaw,
                    silentPitch,
                    player.isOnGround()
            ));
        }

        // Re-aim the crosshair so the attack is actually sent at the selected target.
        client.crosshairTarget = new EntityHitResult(target);
    }

    public void renderHud(DrawContext context, MinecraftClient client, SilentAimConfig config) {
        if (config == null || !config.showHud || client.player == null || client.options.hudHidden) {
            return;
        }

        String state = config.enabled ? (active ? "ON" : "ON (no target)") : "OFF";
        StringBuilder text = new StringBuilder("SilentAim: ").append(state);

        if (active && target != null) {
            text.append(" | ")
                    .append(target.getName().getString())
                    .append(' ')
                    .append(String.format(Locale.ROOT, "%.2f", client.player.distanceTo(target)))
                    .append('m');
        }

        context.drawTextWithShadow(client.textRenderer, text.toString(), 6, 6, 0xFFFFFF);
    }

    public boolean isActive() {
        return active;
    }

    public LivingEntity getTarget() {
        return target;
    }

    private void clear(ClientPlayerEntity player) {
        if (rotationPushed && player != null) {
            popSilentRotation(player);
        }

        rotationPushed = false;
        active = false;
        target = null;
        silentRotationReady = false;
    }
}
