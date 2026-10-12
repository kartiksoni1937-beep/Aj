package hack.echo.backend.logger;

import lombok.Getter;

@Getter
public enum Level {

    DEBUG("DEBUG"),
    INFO("INFO"),
    WARNING("WARNING"),
    ERROR("ERROR"),
    FATAL("FATAL");

    private final String level;

    Level(String level) {
        this.level = level;
    }
}
