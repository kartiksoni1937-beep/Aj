package hack.echo.backend.spring;

import org.eclipse.jetty.server.AsyncRequestLogWriter;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLog;

public final class WebLogger {

    public static RequestLog create() {
        AsyncRequestLogWriter writer = new AsyncRequestLogWriter("logs/access-yyyy_mm_dd.log");
        writer.setRetainDays(31);
        writer.setTimeZone("UTC");
        return new CustomRequestLog(writer, "%{client}a - - %t \"%r\" %s %{CLF}O \"%{Referer}i\" \"%{User-Agent}i\"");
    }
}
