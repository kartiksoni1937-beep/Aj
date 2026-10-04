package hack.echo.client.ui;

import javax.swing.JFrame;

/**
 * Auth removed. This was previously the Swing login dialog. Kept as an empty
 * shell that immediately disposes itself in case anything still references it.
 */
public class LoginFrame extends JFrame {

    public LoginFrame() {
        super("Echo");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        dispose();
    }
}
