package electricity.billing.system.ui;

import electricity.billing.system.client.ApiClient;
import electricity.billing.system.ui.components.Anim;
import electricity.billing.system.ui.components.AuroraBackground;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import electricity.billing.system.ui.components.CenterLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;

/** Start-up screen: shows the brand while checking that the server is reachable. */
public class SplashWindow extends JWindow {

    private static final int MIN_MILLIS = 1500;

    private final Txt status = new Txt("Connecting to server…", Theme.regular(13), () -> new Color(255, 255, 255, 190));
    private final Shimmer shimmer = new Shimmer();
    private final JPanel actions;

    public SplashWindow() {
        setSize(580, 360);
        setLocationRelativeTo(null);
        try {
            setShape(new RoundRectangle2D.Double(0, 0, 580, 360, 28, 28));
        } catch (UnsupportedOperationException ignored) {
            // Square corners on platforms without shaped windows.
        }
        AuroraBackground background = new AuroraBackground(new CenterLayout());
        Txt title = new Txt("Electricity Billing System", Theme.semibold(24), () -> Color.WHITE);
        Txt tagline = new Txt("Power, billed beautifully.", Theme.regular(14), () -> new Color(255, 255, 255, 170));

        Btn retry = new Btn("Try again", Glyph.REFRESH, Btn.Style.PRIMARY);
        retry.addActionListener(e -> check());
        Btn exit = new Btn("Exit", Btn.Style.GHOST) {
            @Override
            public Color getForeground() {
                return Color.WHITE;
            }
        };
        exit.addActionListener(e -> dispose());
        actions = Ui.hbox(10, retry, exit);
        actions.setVisible(false);

        JPanel stack = Ui.vbox(14, IconTile.brand(Glyph.BOLT, 64), title, tagline, Ui.vbox(10, shimmer, status), actions);
        for (Component c : stack.getComponents()) {
            if (c instanceof JComponent j) {
                j.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        background.add(stack);
        setContentPane(background);
    }

    public void start() {
        setVisible(true);
        check();
    }

    private void check() {
        long started = System.currentTimeMillis();
        actions.setVisible(false);
        shimmer.setVisible(true);
        status.setText("Connecting to server…");
        Tasks.run(() -> ApiClient.get().isServerUp(), up -> {
            int wait = (int) Math.max(0, MIN_MILLIS - (System.currentTimeMillis() - started));
            Timer timer = new Timer(wait, e -> {
                if (up) {
                    openSignIn();
                } else {
                    showOffline();
                }
            });
            timer.setRepeats(false);
            timer.start();
        }, error -> showOffline());
    }

    private void showOffline() {
        shimmer.setVisible(false);
        status.setText("Can't reach the server at " + ApiClient.get().baseUrl() + ". Start it, then try again.");
        actions.setVisible(true);
        revalidate();
        repaint();
    }

    private void openSignIn() {
        new AuthWindow().setVisible(true);
        GraphicsDevice screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!screen.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            dispose();
            return;
        }
        Anim.run(260, t -> setOpacity((float) Math.max(0, 1 - t)), this::dispose);
    }

    /** An indeterminate progress line with a light sweeping across it. */
    private static final class Shimmer extends JComponent {
        private final long start = System.nanoTime();
        private final Timer timer = new Timer(16, e -> repaint());

        @Override
        public void addNotify() {
            super.addNotify();
            timer.start();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(260, 4);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fill(new RoundRectangle2D.Double(0, 0, w, 4, 4, 4));
            double t = ((System.nanoTime() - start) / 1e9 * 0.8) % 1.0;
            double x = -w * 0.4 + t * w * 1.4;
            g2.setClip(new RoundRectangle2D.Double(0, 0, w, 4, 4, 4));
            g2.setPaint(new GradientPaint((float) x, 0, Theme.alpha(Theme.AQUA, 0), (float) (x + w * 0.2), 0, Theme.AQUA, true));
            g2.fill(new RoundRectangle2D.Double(x, 0, w * 0.4, 4, 4, 4));
            g2.dispose();
        }
    }
}
