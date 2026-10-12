package dev.silentaim.aim;

import dev.silentaim.SilentAimConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Picks the best entity to aim at: closest to the crosshair, in range, with optional line of sight.
 */
public final class TargetSelector {

    private TargetSelector() {
    }

    public static LivingEntity select(MinecraftClient client, SilentAimConfig config) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return null;
        }

        Vec3d eye = player.getEyePos();
        Box searchBox = player.getBoundingBox().expand(config.range);
        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                entity -> isValid(player, config, entity)
        );

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        float halfFov = (float) (config.fov * 0.5);

        for (LivingEntity candidate : candidates) {
            Vec3d point = aimPoint(candidate);
            float[] rotation = RotationMath.lookAt(eye, point);

            float yawOffset = RotationMath.offset(rotation[0], player.getYaw());
            float pitchOffset = RotationMath.offset(rotation[1], player.getPitch());
            if (yawOffset > halfFov || pitchOffset > halfFov) {
                continue;
            }

            if (!config.throughWalls && !hasLineOfSight(world, player, eye, point)) {
                continue;
            }

            double score = Math.max(yawOffset, pitchOffset) + player.squaredDistanceTo(candidate) * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Slightly above the middle of the hitbox: the part of a player that actually gets hit.
     */
    public static Vec3d aimPoint(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        double y = box.minY + (box.maxY - box.minY) * 0.85;
        return new Vec3d((box.minX + box.maxX) * 0.5, y, (box.minZ + box.maxZ) * 0.5);
    }

    private static boolean isValid(ClientPlayerEntity player, SilentAimConfig config, LivingEntity entity) {
        if (entity == player) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved() || entity.isInvisible()) {
            return false;
        }
        if (player.squaredDistanceTo(entity) > config.range * config.range) {
            return false;
        }

        if (entity instanceof PlayerEntity other) {
            return config.targetPlayers && !other.isCreative() && !other.isSpectator();
        }

        if (entity instanceof MobEntity) {
            return config.targetMobs;
        }

        return false;
    }

    private static boolean hasLineOfSight(ClientWorld world, ClientPlayerEntity player, Vec3d from, Vec3d to) {
        BlockHitResult hit = world.raycast(new RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }
}
