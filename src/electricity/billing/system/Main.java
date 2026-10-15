package electricity.billing.system;

import electricity.billing.system.ui.SplashWindow;
import electricity.billing.system.ui.theme.Theme;
import javax.swing.SwingUtilities;

/** Starts the desktop app: installs the theme, then shows the splash screen. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new SplashWindow().start();
        });
    }
}
