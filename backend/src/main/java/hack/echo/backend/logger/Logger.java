package hack.echo.backend.logger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

import static hack.echo.backend.logger.Ansi.Color.*;
import static hack.echo.backend.logger.Ansi.Attribute.*;

public class Logger {

    public static final Logger INSTANCE = new Logger();

    private final StringBuilder output = new StringBuilder();
    private final StringBuilder copy = new StringBuilder();
    private final DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private Level level = Level.DEBUG;

    private final ReentrantLock lock = new ReentrantLock();

    public Logger() {}

    public Logger(Level level) {
        this.level = level;
    }

    public synchronized void debug(Object... args) {
        logln(Level.DEBUG, args);
    }

    public synchronized void info(Object... args) {
        logln(Level.INFO, args);
    }

    public synchronized void warn(Object... args) {
        logln(Level.WARNING, args);
    }

    public  synchronized void error(Object... args) {
        logln(Level.ERROR, args);
    }

    public synchronized void fatal(Object... args) {
        logln(Level.FATAL, args);
    }

    public synchronized void rlog(Level level, Object... objects) {
        if (level.ordinal() < this.level.ordinal()) {
            return;
        }
        lock.lock();

        String start = String.format("\r[%s] [%s] ", dateFormat.format(new Date()), level.getLevel());

        for (var obj : objects) {
            System.out.printf("%s%s", start, obj);
            append(start, obj);
        }
        lock.unlock();
    }

    public synchronized void log(Level level, Object... objects) {
        if (level.ordinal() < this.level.ordinal()) {
            return;
        }

        lock.lock();

        String start = String.format("[%s] [%s] ", dateFormat.format(new Date()), level.getLevel());

        PrintStream out = System.out;

        for (var obj : objects) {
            String color = String.format("%s%sm", Ansi.esc, switch (level) {
                case WARNING -> BRIGHT_YELLOW.getCode();
                case ERROR, FATAL -> BRIGHT_RED.getCode();
                default -> RESET.getCode();
            });
            String reset = String.format("%s%sm", Ansi.esc, RESET.getCode());
            if (obj instanceof Throwable) {
                out.println();
                append("\n");
            }
            out.printf("%s%s%s%s", color, start, obj, reset);
            append(String.format("%s%s%s%s", color, start, obj, reset), String.format("%s%s", start, obj));
            if (obj instanceof Throwable throwable) {
                StringBuilder sb = new StringBuilder();
                for (StackTraceElement ste : throwable.getStackTrace()) {
                    String traceLine = String.format("%n\t%sat %s.%s(%s:%d)%s", color, ste.getClassName(), ste.getMethodName(), ste.getFileName(), ste.getLineNumber(), reset);
                    sb.append(traceLine);
                    out.print(traceLine);
                }
                append(sb.toString(), sb.toString());
            }
        }
        lock.unlock();
    }

    public synchronized void logln(Level level, Object... objects) {
        if (level.ordinal() < this.level.ordinal()) {
            return;
        }
        lock.lock();
        log(level, objects);
        System.out.println();
        append("\n");
        lock.unlock();
    }

    private void append(String log, String raw) {
        output.append(raw);
        copy.append(log);
    }

    public void append(Object... objects) {
        for (var obj : objects) {
            copy.append(obj);
        }
    }

    public String getLog() {
        String log = copy.toString();
        copy.setLength(0);
        return log;
    }

    public void clear() {
        copy.setLength(0);
    }

    public void dump() {
        dump("latest-echo.txt");
    }

    public void dump(String path) {
        try {
            Files.write(Paths.get(path), output.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
