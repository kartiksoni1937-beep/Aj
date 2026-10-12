package hack.echo.loader.auth;

import hack.echo.loader.PreEcholoader;
import hack.echo.loader.classloader.CustomClassLoader;
import org.apache.commons.lang3.function.TriFunction;
import war.jnt.api.CustomArray;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;

public class LoginFrame extends JFrame {

    private final CustomClassLoader classLoader;
    private final CompletableFuture<Boolean> loginFuture = new CompletableFuture<>();

    private JTextField usernameField;
    private JTextField passwordField;

    private final List<Character> actualUsername = new ArrayList<>();
    private final List<Character> actualPassword = new ArrayList<>();

    private JButton loginButton;
    private JLabel statusLabel;

    public LoginFrame(CustomClassLoader classLoader) {
        this.classLoader = classLoader;

        setTitle("Echo Prot 0.4B Login");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen
        setResizable(false);

        initUI();
    }

    private void initUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel userPanel = new JPanel(new BorderLayout());
        userPanel.add(new JLabel("Username:"), BorderLayout.WEST);
        usernameField = new JTextField();

        ((AbstractDocument) usernameField.getDocument()).setDocumentFilter(new DocumentFilter() {

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                for (int i = 0; i < string.length(); i++) {
                    actualUsername.add(offset + i, string.charAt(i));
                }

                super.insertString(fb, offset, string, attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                // Remove old characters
                for (int i = 0; i < length; i++) {
                    if (offset < actualUsername.size()) {
                        actualUsername.remove(offset);
                    }
                }

                // Insert new characters
                for (int i = 0; i < text.length(); i++) {
                    actualUsername.add(offset + i, text.charAt(i));
                }

                super.replace(fb, offset, length, text, attrs);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                for (int i = 0; i < length; i++) {
                    if (offset < actualUsername.size()) {
                        actualUsername.remove(offset);
                    }
                }

                super.remove(fb, offset, length);
            }
        });

        userPanel.add(usernameField, BorderLayout.CENTER);
        panel.add(userPanel);

        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        JPanel passPanel = new JPanel(new BorderLayout());
        passPanel.add(new JLabel("Password:"), BorderLayout.WEST);
        passwordField = new JTextField();

        // Add custom document filter to hide characters
        ((AbstractDocument) passwordField.getDocument()).setDocumentFilter(new DocumentFilter() {

            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                for (int i = 0; i < string.length(); i++) {
                    actualPassword.add(offset + i, string.charAt(i));
                }

                super.insertString(fb, offset, "•".repeat(string.length()), attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                // Remove old characters
                for (int i = 0; i < length; i++) {
                    if (offset < actualPassword.size()) {
                        actualPassword.remove(offset);
                    }
                }

                // Insert new characters
                for (int i = 0; i < text.length(); i++) {
                    actualPassword.add(offset + i, text.charAt(i));
                }

                super.replace(fb, offset, length, "•".repeat(text.length()), attrs);
            }

            @Override
            public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                for (int i = 0; i < length; i++) {
                    if (offset < actualPassword.size()) {
                        actualPassword.remove(offset);
                    }
                }

                super.remove(fb, offset, length);
            }
        });

        passPanel.add(passwordField, BorderLayout.CENTER);
        panel.add(passPanel);

        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.addActionListener(e -> attemptLogin());
        panel.add(loginButton);

        panel.add(Box.createRigidArea(new Dimension(0, 10)));

        statusLabel = new JLabel("Waiting for login...");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(statusLabel);

        add(panel);

        // Allow Enter key to trigger login
        getRootPane().setDefaultButton(loginButton);
    }

    private char[] listToCharArray(List<Character> list) {
        char[] array = new char[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }

        return array;
    }

    private void clearCharList(List<Character> list) {
        Collections.fill(list, '\0');
        list.clear();
    }

    @CustomArray
    private void attemptLogin() {
        if (actualUsername.isEmpty() || actualPassword.isEmpty()) {
            statusLabel.setText("Please enter credentials");
            statusLabel.setForeground(Color.RED);
            return;
        }

        statusLabel.setText("Logging in...");
        statusLabel.setForeground(Color.BLACK);
        loginButton.setEnabled(false);
        usernameField.setEnabled(false);
        passwordField.setEnabled(false);

        // Convert to char arrays for secure handling
        char[] username = listToCharArray(actualUsername);
        char[] password = listToCharArray(actualPassword);

        // Convert to String only for the auth call (necessary for WebSocket)
        String usernameStr = new String(username);
        String passwordStr = new String(password);

        // Clear char arrays immediately after converting to String
        //Arrays.fill(username, '\0');
        for (int i = 0; i < username.length; i++) {
            username[i] = '\0';
        }

        //Arrays.fill(password, '\0');
        for (int i = 0; i < password.length; i++) {
            password[i] = '\0';
        }

        BiFunction<Boolean, byte[], Void> loginResponse = (success, payload) -> {
            System.out.println("Login response received: " + success);
            if (success) {
                statusLabel.setText("Success!");
                statusLabel.setForeground(Color.GREEN);

                // Clear credentials from memory
                clearCharList(actualUsername);
                clearCharList(actualPassword);
                usernameField.setText("");
                passwordField.setText("");

                classLoader.load(payload);
                dispose(); // Close the window
                loginFuture.complete(true);
            } else {
                statusLabel.setText("Login failed");
                statusLabel.setForeground(Color.RED);
                loginButton.setEnabled(true);
                usernameField.setEnabled(true);
                passwordField.setEnabled(true);

                // Clear password on failed login
                clearCharList(actualPassword);
                passwordField.setText("");
            }
            return null;
        };

        try {
            String[] callInfo = PreEcholoader.initAuthClass.split(":");
           // Class<?> authClass = classLoader.loadClass("hack.echo.auth.Main");
            Class<?> authClass = classLoader.loadClass(callInfo[0]);
            //authClass.getMethod("init", String.class, String.class, BiFunction.class).invoke(null, usernameStr, passwordStr, loginResponse);
            authClass.getMethod(callInfo[1], String.class, String.class, BiFunction.class).invoke(null, usernameStr, passwordStr, loginResponse);
        } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        /*authManager.login(usernameStr, passwordStr, "manual").thenAccept(success -> {
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    statusLabel.setText("Success!");
                    statusLabel.setForeground(Color.GREEN);

                    // Clear credentials from memory
                    clearCharList(actualUsername);
                    clearCharList(actualPassword);
                    usernameField.setText("");
                    passwordField.setText("");

                    loginFuture.complete(true);
                    dispose(); // Close the window
                } else {
                    statusLabel.setText("Login failed");
                    statusLabel.setForeground(Color.RED);
                    loginButton.setEnabled(true);
                    usernameField.setEnabled(true);
                    passwordField.setEnabled(true);

                    // Clear password on failed login
                    clearCharList(actualPassword);
                    passwordField.setText("");
                }
            });
        });*/
    }

    public CompletableFuture<Boolean> waitForLogin() {
        setVisible(true);
        return loginFuture;
    }
}