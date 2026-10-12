package hack.echo.backend.spring.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {

    boolean adminOnly() default false;

    boolean subOnly() default false;

    boolean noMessage() default false;
}