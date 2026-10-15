package electricity.billing.system;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Splash screen shown for two seconds before the login window. */
public class Main extends BaseFrame {

    private static final int SPLASH_MILLIS = 2000;

    Main() {
        super("Electricity Billing System");
        add(new JLabel(icon("Welcome.jpg")));
    }

    void showSplash() {
        setVisible(true);
        // A Swing Timer fires on the event thread, so the windows are changed safely.
        Timer timer = new Timer(SPLASH_MILLIS, e -> {
            dispose();
            new Login();
        });
        timer.setRepeats(false);
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().showSplash());
    }
}
