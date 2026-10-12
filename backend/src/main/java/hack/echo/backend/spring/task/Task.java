package hack.echo.backend.spring.task;

public interface Task {

    void cleanup();

    default long getIntervalMillis() {
        return 60000L;
    }
}
