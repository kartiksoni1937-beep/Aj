package hack.echo.backend.spring.ratelimit;

import com.google.gson.JsonObject;
import hack.echo.backend.util.WebUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String className = handlerMethod.getBeanType().getSimpleName();
        String methodName = handlerMethod.getMethod().getName();

        String key = String.format("%s:%s", WebUtil.getClientIP(request), String.format("%s.%s", className, methodName));
        Duration window = Duration.of(rateLimit.windowTime(), rateLimit.unit().toChronoUnit());

        if (!rateLimiter.tryAcquire(key, rateLimit.limit(), window)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            JsonObject responseObj = new JsonObject();
            responseObj.addProperty("success", false);
            responseObj.addProperty("message", "Too Many Requests");
            response.getWriter().write(responseObj.toString());
            return false;
        }

        return true;
    }
}