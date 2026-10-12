package hack.echo.loader.auth;

import hack.echo.loader.PreEcholoader;
import hack.echo.loader.classloader.CustomClassLoader;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class LoadingFrame extends JFrame {

    private final CustomClassLoader classLoader;
    private final CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();

    private JLabel statusLabel;

    public LoadingFrame(CustomClassLoader classLoader) {
        this.classLoader = classLoader;

        setTitle("Echo Prot 0.4B Login");
        setSize(300, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(50, 20, 50, 20));

        statusLabel = new JLabel("Loading...");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(statusLabel);

        add(panel);

        BiFunction<Boolean, byte[], Void> loginResponse = (success, payload) -> {
            if (success) {
                statusLabel.setText("Success!");
                statusLabel.setForeground(Color.GREEN);

                classLoader.load(payload);
                dispose();
                loginFuture.complete(true);
            } else {
                statusLabel.setText("Login failed");
                statusLabel.setForeground(Color.RED);
                dispose();
                loginFuture.complete(false);
            }
            return null;
        };

        try {
            String[] callInfo = PreEcholoader.initAuthClass.split(":");
            // Class<?> authClass = classLoader.loadClass("hack.echo.auth.Main");
            Class<?> authClass = classLoader.loadClass(callInfo[0]);
            //authClass.getMethod("init", String.class, String.class, BiFunction.class).invoke(null, usernameStr, passwordStr, loginResponse);
            authClass.getMethod(callInfo[1], String.class, String.class, BiFunction.class).invoke(null, "HWID_LOGIN_TEMPLATE", "12345678", loginResponse);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public CompletableFuture<Boolean> waitForLogin() {
        setVisible(true);
        return loginFuture;
    }
}
