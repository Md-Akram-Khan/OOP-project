package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The animated backdrop for the splash and sign-in screens: drifting aurora
 * glows over deep navy, a faint dot grid, and "power lines" with energy pulses
 * running along them. Animates only while on screen.
 */
public class AuroraBackground extends JPanel {

    private static final int FPS_MS = 33;
    private static final int GLOW_SCALE = 6;

    private final Timer timer;
    private final long start = System.nanoTime();
    private double t;

    public AuroraBackground(LayoutManager layout) {
        super(layout);
        setOpaque(true);
        timer = new Timer(FPS_MS, e -> {
            t = (System.nanoTime() - start) / 1e9;
            repaint();
        });
    }

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
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, Theme.INK_TOP, w * 0.4f, h, new Color(0x0D1B42)));
        g2.fillRect(0, 0, w, h);

        // Glows are drawn small and scaled up: soft, and cheap enough for 30 fps.
        int sw = Math.max(1, w / GLOW_SCALE);
        int sh = Math.max(1, h / GLOW_SCALE);
        BufferedImage glow = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = glow.createGraphics();
        blob(gg, sw * (0.22 + 0.08 * Math.sin(t * 0.31)), sh * (0.28 + 0.10 * Math.cos(t * 0.23)), Math.max(sw, sh) * 0.62, Theme.BLUE, 150);
        blob(gg, sw * (0.78 + 0.09 * Math.cos(t * 0.27)), sh * (0.62 + 0.10 * Math.sin(t * 0.21)), Math.max(sw, sh) * 0.55, Theme.AQUA, 105);
        blob(gg, sw * (0.55 + 0.15 * Math.sin(t * 0.17)), sh * (0.12 + 0.06 * Math.cos(t * 0.35)), Math.max(sw, sh) * 0.32, Theme.VIOLET, 80);
        blob(gg, sw * (0.30 + 0.12 * Math.cos(t * 0.19)), sh * (0.92 + 0.04 * Math.sin(t * 0.4)), Math.max(sw, sh) * 0.26, Theme.AMBER, 45);
        gg.dispose();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(glow, 0, 0, w, h, null);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Faint dot grid
        g2.setColor(new Color(255, 255, 255, 14));
        for (int y = 16; y < h; y += 28) {
            for (int x = 16; x < w; x += 28) {
                g2.fillRect(x, y, 2, 2);
            }
        }

        // Power lines with travelling pulses
        for (int i = 0; i < 4; i++) {
            double baseY = h * (0.70 + i * 0.055);
            double amp = 14 + i * 5;
            double speed = 0.45 + i * 0.12;
            double freq = 0.0065 - i * 0.0008;
            Path2D line = new Path2D.Double();
            for (int x = 0; x <= w; x += 8) {
                double y = baseY + Math.sin(x * freq + t * speed + i) * amp;
                if (x == 0) {
                    line.moveTo(x, y);
                } else {
                    line.lineTo(x, y);
                }
            }
            Color c = i % 2 == 0 ? Theme.AQUA : Theme.BLUE;
            g2.setStroke(new BasicStroke(1.4f));
            g2.setPaint(new GradientPaint(0, 0, Theme.alpha(c, 0), w * 0.5f, 0, Theme.alpha(c, 120 - i * 18), true));
            g2.draw(line);

            double px = ((t * (90 + i * 25) + i * 260) % (w + 200)) - 100;
            double py = baseY + Math.sin(px * freq + t * speed + i) * amp;
            for (int r = 14; r >= 2; r -= 3) {
                g2.setColor(Theme.alpha(c, (int) (90 * (1 - r / 16.0))));
                g2.fill(new Ellipse2D.Double(px - r, py - r, r * 2, r * 2));
            }
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
        }
        g2.dispose();
    }

    private static void blob(Graphics2D g, double cx, double cy, double radius, Color color, int alpha) {
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Double(cx, cy), (float) radius,
                new float[] {0f, 1f}, new Color[] {Theme.alpha(color, alpha), Theme.alpha(color, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g.setPaint(paint);
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
    }
}
