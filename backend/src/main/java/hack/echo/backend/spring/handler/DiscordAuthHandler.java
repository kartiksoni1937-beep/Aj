package hack.echo.backend.spring.handler;

import hack.echo.backend.spring.task.Task;
import hack.echo.backend.util.WebUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DiscordAuthHandler implements Task {

    private final Map<String, AuthCode> authCodes = new HashMap<>();
    private final Map<String, AuthCode> pendingLinks = new HashMap<>();

    public String storeLinkingState(String username) {
        String token = WebUtil.randomHex(32);
        pendingLinks.put(token, new AuthCode(username, System.currentTimeMillis() + 10 * 60 * 1000));
        return token;
    }

    public String exchangeLinkingState(String token) {
        AuthCode link = pendingLinks.remove(token);
        if (link == null || link.expiresIn < System.currentTimeMillis()) {
            return null;
        }
        return link.token;
    }

    public String storeAuthCode(String token, int expiresIn) {
        String code = WebUtil.randomHex(32);
        authCodes.put(code, new AuthCode(token, System.currentTimeMillis() + expiresIn));
        return code;
    }

    public String exchangeAuthCode(String code) {
        AuthCode authCode = authCodes.get(code);

        if (authCode == null) {
            return null;
        }

        if (authCode.expiresIn < System.currentTimeMillis()) {
            authCodes.remove(code);
            return null;
        }

        authCodes.remove(code);
        return authCode.token;
    }

    @Override
    public void cleanup() {
        authCodes.entrySet().removeIf(entry -> entry.getValue().expiresIn < System.currentTimeMillis());
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().expiresIn < System.currentTimeMillis());
    }

    private static class AuthCode {

        String token;
        long expiresIn;

        public AuthCode(String token, long expiresIn) {
            this.token = token;
            this.expiresIn = expiresIn;
        }
    }
}
