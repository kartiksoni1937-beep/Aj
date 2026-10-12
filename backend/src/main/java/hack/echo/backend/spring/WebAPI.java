package hack.echo.backend.spring;

import hack.echo.backend.Backend;
import hack.echo.backend.spring.task.Task;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.Callback;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WebAPI {

    private static volatile AnnotationConfigWebApplicationContext webContext;

    public static void start(int port) {
        Path p = Paths.get("logs");
        if (!p.toFile().exists()) {
            p.toFile().mkdirs();
        }

        Thread thread = new Thread(() -> {
            AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
            context.register(WebConfig.class);
            webContext = context;

            DispatcherServlet dispatcherServlet = new DispatcherServlet(context);
            ServletHolder servletHolder = new ServletHolder(dispatcherServlet);

            ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
            contextHandler.setContextPath("/");
            contextHandler.addServlet(servletHolder, "/*");
            contextHandler.setErrorHandler(new ErrorHandler() {

                @Override
                public boolean handle(Request request, Response response, Callback callback) throws Exception {
                    if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
                        return false;
                    }
                    super.handle(request, response, callback);
                    return true;
                }
            });

            Server server = new Server();
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.setSendServerVersion(false);

            HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);
            ServerConnector connector = new ServerConnector(server, httpFactory);
            connector.setPort(port);

            server.addConnector(connector);
            server.setRequestLog(WebLogger.create());
            server.setHandler(contextHandler);

            try {
                server.start();
                server.join();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "web-api-thread");

        thread.setDaemon(false);
        thread.start();

        Thread taskThread = new Thread(() -> {
            Map<String, Long> lastRunTimes = new HashMap<>();
            while (true) {
                try {
                    if (webContext != null && webContext.isActive()) {
                        long now = System.currentTimeMillis();
                        Map<String, Task> cleanupBeans = webContext.getBeansOfType(Task.class);
                        for (Map.Entry<String, Task> entry : cleanupBeans.entrySet()) {
                            String beanName = entry.getKey();
                            Task cleanupBean = entry.getValue();

                            if (now - lastRunTimes.getOrDefault(beanName, 0L) < Math.max(1000L, cleanupBean.getIntervalMillis())) {
                                continue;
                            }

                            try {
                                cleanupBean.cleanup();
                                lastRunTimes.put(beanName, now);
                            } catch (Exception exception) {
                                Backend.logger.error("Failed to execute cleanup task", exception);
                            }
                        }
                    }

                    Thread.sleep(60000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception exception) {
                    Backend.logger.error("Task thread failure", exception);
                }
            }
        }, "web-api-task-thread");
        taskThread.setDaemon(true);
        taskThread.start();
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (webContext == null || !webContext.isActive()) {
            throw new IllegalStateException("Web context is not ready");
        }

        return webContext.getBean(beanClass);
    }
}
