package hack.echo.client.auth;

/**
 * Auth removed. This was previously a WebSocket client against the Echo auth
 * server (login, HWID binding, session tokens, heartbeat timeouts). All of that
 * machinery is gone; the manager now reports an always-authenticated state so
 * every gate that used to consult it passes unconditionally.
 */
public class AuthManager {

    public int requestPacket = -1;

    public boolean isLoggedIn() {
        return true;
    }

    public boolean validateSession() {
        return true;
    }

    public boolean validateWebSocket() {
        return true;
    }

    public boolean validateUid() {
        return true;
    }

    public boolean isSessionExpired() {
        return false;
    }

    public boolean isConnectionTimeout() {
        return false;
    }

    /**
     * Auth state bitfield: all four bits set = previously "fully authenticated".
     */
    public int getAuthState() {
        return 0x0F;
    }

    public int getAuthFingerprintTarget() {
        return obscureFingerprint(0x0F);
    }

    public int getAuthFingerprint() {
        return obscureFingerprint(getAuthState());
    }

    public static int obscureFingerprint(int state) {
        int nibble = state & 0x0F;
        nibble = ((nibble * 0x0B) ^ 0x05) & 0x0F;
        nibble = ((nibble << 1) | (nibble >>> 3)) & 0x0F;

        int mirrored = (nibble << 4) | (~nibble & 0x0F);
        int twist = (mirrored ^ 0xA7) + ((mirrored & 0x3C) << 1);
        twist ^= (twist >>> 3) | (twist << 5);
        return twist & 0xFF;
    }

    public void shutdown() {
        // No-op now that there is no session to tear down.
    }
}
