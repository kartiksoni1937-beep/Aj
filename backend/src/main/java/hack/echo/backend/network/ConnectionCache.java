package hack.echo.backend.network;

import lombok.Getter;

import javax.crypto.SecretKey;

@Getter
public class ConnectionCache {

    private final String hwid;
    private final SecretKey sSecretKey;
    private final SecretKey cSecretKey;
    private final long added;

    public ConnectionCache(String hwid, SecretKey cSecretKey, SecretKey sSecretKey) {
        this.hwid = hwid;
        this.cSecretKey = cSecretKey;
        this.sSecretKey = sSecretKey;
        added = System.currentTimeMillis();
    }
}
