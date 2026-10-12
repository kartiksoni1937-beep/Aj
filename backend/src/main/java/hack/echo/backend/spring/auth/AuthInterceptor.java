package hack.echo.backend.spring.auth;

import com.google.gson.JsonObject;
import hack.echo.backend.network.User;
import hack.echo.backend.spring.service.StripeSubscriptionSync;
import hack.echo.backend.util.MongoUtil;
import hack.echo.backend.util.WebUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private StripeSubscriptionSync stripeSubscriptionSync;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireAuth requireAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        if (requireAuth == null) {
            return true;
        }

        String jwtToken = request.getHeader("Authorization");
        if (jwtToken == null || !WebUtil.isTokenValid(jwtToken.replace("Bearer ", ""))) {
            sendError(response, requireAuth, "Invalid or expired session token");
            return false;
        }

        jwtToken = jwtToken.replace("Bearer ", "");
        User user = MongoUtil.getUser("username", WebUtil.getUsername(jwtToken));
        if (user == null) {
            sendError(response, requireAuth, "Invalid or expired session token");
            return false;
        }

        if (requireAuth.adminOnly() && !user.isAdmin()) {
            sendError(response, requireAuth, "Admin privileges required");
            return false;
        }

        if (requireAuth.subOnly()) {
            user = stripeSubscriptionSync.refreshUserIfStripeCanFixAccess(user);
        }

        if (requireAuth.subOnly() && !stripeSubscriptionSync.hasActiveLicense(user)) {
            sendError(response, requireAuth, "Subscription required");
            return false;
        }

        return true;
    }

    private void sendError(HttpServletResponse response, RequireAuth auth, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        JsonObject responseObj = new JsonObject();
        responseObj.addProperty("success", false);
        if (!auth.noMessage()) {
            responseObj.addProperty("message", message);
        } else {
            response.setStatus(HttpStatus.OK.value());
        }
        response.getWriter().write(responseObj.toString());
    }
}
