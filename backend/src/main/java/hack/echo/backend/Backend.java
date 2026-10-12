package hack.echo.backend;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import hack.echo.backend.logger.Level;
import hack.echo.backend.logger.Logger;
import hack.echo.backend.spring.WebAPI;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import hack.echo.backend.command.CommandManager;
import hack.echo.backend.config.Config;
import hack.echo.backend.network.Server;
import lombok.Setter;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import javax.crypto.SecretKey;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Getter
public class Backend {

    private final Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 4, 65536, 3);

    @Getter
    private static final Backend instance = new Backend();
    public static final Logger logger = new Logger(Level.INFO);
    private final CommandManager commandManager = new CommandManager();
    private final Server server = new Server();
    private Config config = new Config();
    private MongoDatabase database;
    @Setter
    private boolean maintenanceMode = false;
    private SecretKey jwtKey;

    public void start() {
        try {
            MongoClient mongoClient = MongoClients.create(config.database);
            database = mongoClient.getDatabase(config.dbName);
        } catch (Exception e) {
            logger.fatal("Failed to connect to database", e);
            System.exit(1);
        }

        commandManager.init();
        server.init(config.port);

        jwtKey = Keys.hmacShaKeyFor(config.jwtSecret.getBytes(StandardCharsets.UTF_8));

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        WebAPI.start(config.backendPort);
        while (true) {
            try {
                String command = reader.readLine();
                if (command == null || command.trim().isEmpty()) {
                    continue;
                }
                commandManager.runCommand(command.split(" "));
            } catch (Exception e) {
                logger.error("Failed to execute command", e);
            }
        }
    }

    public void reloadConfig() {
        config = new Config();
    }
}
