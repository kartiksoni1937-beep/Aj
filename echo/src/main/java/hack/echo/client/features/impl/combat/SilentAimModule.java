package hack.echo.client.features.impl.combat;

import hack.echo.client.event.EventSubscribe;
import hack.echo.client.event.impl.EventStartAttack;
import hack.echo.client.features.Category;
import hack.echo.client.features.Feature;
import hack.echo.client.features.FeatureInfo;
import hack.echo.client.features.settings.impl.BoolSetting;
import hack.echo.client.features.settings.impl.FloatSetting;
import hack.echo.client.utils.combat.CombatUtils;
import hack.echo.client.utils.combat.TargetUtils;
import hack.echo.client.utils.rotation.RotationUtils;
import hack.echo.client.utils.strings.Concat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Silent aim: attacking with nothing (or with an invalid entity) under the crosshair
 * redirects the attack onto the closest valid target inside the range/FOV cone.
 * Only the rotation the server receives points at the target - the camera never moves.
 */
public class SilentAimModule extends Feature {

    public SilentAimModule() {
        super(new FeatureInfo(
            Concat.of("Silent Aim"),
            Concat.of("Redirects missed attacks onto the closest target"),
            Category.COMBAT
        ));
    }

    private final BoolSetting silent = new BoolSetting(Concat.of("Silent"), true);
    private final BoolSetting throughWalls = new BoolSetting(Concat.of("Through Walls"), false);
    private final BoolSetting random = new BoolSetting(Concat.of("Random"), false);
    private final FloatSetting range = new FloatSetting(Concat.of("Range"), 4.5f, 1.0f, 6.0f, 0.1f);
    private final FloatSetting fov = new FloatSetting(Concat.of("FOV"), 180.0f, 1.0f, 360.0f, 1.0f);
    private final FloatSetting jitter = new FloatSetting(Concat.of("Jitter"), 0.35f, 0.0f, 5.0f, 0.05f);

    @Override
    public void onDisable() {
        super.onDisable();
        RotationUtils.resetState();
    }

    @EventSubscribe
    private void onAttack(EventStartAttack event) {
        if (isNull() || mc.level == null) return;

        // The crosshair already sits on a valid target, so the hit lands on its own.
        if (event.getTarget() instanceof LivingEntity crosshair && TargetUtils.isTargetAllowed(crosshair)) return;

        LivingEntity target = findTarget();
        if (target == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float[] aim = aimAt(target, partialTick);

        RotationUtils.forceSilentRotations(aim[0], aim[1]);

        // Non-silent mode only turns the player, the attack itself stays where it was aimed.
        if (!silent.getValue()) return;

        mc.hitResult = new EntityHitResult(target);
    }

    private LivingEntity findTarget() {
        double rangeValue = range.getValue();
        double rangeSquared = rangeValue * rangeValue;

        List<LivingEntity> candidates = mc.level.getEntitiesOfClass(
                LivingEntity.class,
                mc.player.getBoundingBox().inflate(rangeValue),
                entity -> entity != mc.player && TargetUtils.isTargetAllowed(entity)
            ).stream()
            .filter(entity -> mc.player.distanceToSqr(entity) <= rangeSquared)
            .filter(this::isInFov)
            .filter(this::hasLineOfSight)
            .toList();

        LivingEntity nearest = CombatUtils.selectNearestTarget(mc.player, candidates);
        return nearest == null ? null : TargetUtils.resolveTarget(nearest);
    }

    private boolean isInFov(LivingEntity entity) {
        return CombatUtils.isInFOV(mc.player, entity, fov.getValue());
    }

    private boolean hasLineOfSight(LivingEntity entity) {
        if (throughWalls.getValue()) return true;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 targetEye = entity.getEyePosition(partialTick);
        ClipContext context = new ClipContext(eye, targetEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        return mc.level.clip(context).getType() == HitResult.Type.MISS;
    }

    private float[] aimAt(LivingEntity target, float partialTick) {
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 diff = target.getEyePosition(partialTick).subtract(eye);
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontal)));

        if (random.getValue()) {
            ThreadLocalRandom localRandom = ThreadLocalRandom.current();
            float offset = jitter.getValue();
            yaw += (float) localRandom.nextDouble(-offset, offset);
            pitch += (float) localRandom.nextDouble(-offset, offset);
        }

        // Stay continuous with what was last sent so the server never sees a wrapped angle.
        float baseYaw = RotationUtils.hasSilentRotation() ? RotationUtils.getSilentYaw() : mc.player.getYRot();
        float basePitch = RotationUtils.hasSilentRotation() ? RotationUtils.getSilentPitch() : mc.player.getXRot();
        float[] continuous = RotationUtils.ensureContinuity(new float[]{baseYaw, basePitch}, new float[]{yaw, pitch});
        return new float[]{continuous[0], Mth.clamp(continuous[1], -90f, 90f)};
    }
}
