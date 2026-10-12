package dev.silentaim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple persisted configuration for the standalone SilentAim mod.
 */
public final class SilentAimConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SilentAimClient.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("silentaim.json");

    /** Master switch, toggled with the keybind. */
    public boolean enabled = false;

    /** When true the rotation is only sent to the server (camera stays where you look). */
    public boolean silent = true;

    public boolean targetPlayers = true;
    public boolean targetMobs = false;

    /** Ignore line of sight when picking and hitting targets. */
    public boolean throughWalls = false;

    public boolean showHud = true;

    /** Add a small random offset before smoothing the rotation. */
    public boolean randomize = false;

    public double randomJitter = 0.35;

    /** Maximum target distance in blocks. */
    public double range = 4.5;

    /** Full angle cone around the crosshair that targets must be inside, in degrees. */
    public double fov = 180.0;

    /** Rotation smoothing speed in degrees per tick. 180 is close to instant. */
    public double speed = 180.0;

    public static SilentAimConfig load() {
        try {
            if (Files.exists(PATH)) {
                SilentAimConfig loaded = GSON.fromJson(Files.readString(PATH), SilentAimConfig.class);
                if (loaded != null) {
                    return loaded.clamped();
                }
            }
        } catch (Exception exception) {
            LOGGER.warn("[SilentAim] Could not read {}, using defaults", PATH, exception);
        }
        return new SilentAimConfig();
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception exception) {
            LOGGER.warn("[SilentAim] Could not write {}", PATH, exception);
        }
    }

    private SilentAimConfig clamped() {
        range = Math.max(0.5, Math.min(64.0, range));
        fov = Math.max(1.0, Math.min(360.0, fov));
        speed = Math.max(1.0, Math.min(180.0, speed));
        randomJitter = Math.max(0.0, Math.min(10.0, randomJitter));
        return this;
    }
}
