package hack.echo.client.auth;

import net.minecraft.util.Mth;

/**
 * Auth removed. The enforced-pitch verification (which forced the camera to
 * -90 whenever the auth server's runtime constant did not match) is gone; the
 * requested pitch is now clamped and returned unchanged.
 */
public class MathProt {

    public static float getEnforcedPitch(float requestedPitch) {
        return Mth.clamp(requestedPitch, -90f, 90f);
    }
}