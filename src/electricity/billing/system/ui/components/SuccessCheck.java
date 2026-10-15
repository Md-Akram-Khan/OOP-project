package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/** A success mark that draws itself: a ring sweeps round, the disc fills, then the tick is drawn. */
public class SuccessCheck extends JComponent {

    private static final int SIZE = 96;
    private double t = 1;

    public void play() {
        Anim.run(1000, p -> {
            t = p;
            repaint();
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE + 40, SIZE + 40);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double x = (getWidth() - SIZE) / 2.0;
        double y = (getHeight() - SIZE) / 2.0;

        double glow = Math.min(1, t * 1.4);
        for (int i = 20; i >= 2; i -= 3) {
            g2.setColor(Theme.alpha(Theme.SUCCESS, (int) (glow * 40 * (1 - i / 22.0))));
            g2.fill(new Ellipse2D.Double(x - i, y - i, SIZE + i * 2, SIZE + i * 2));
        }
        double ring = Math.min(1, t / 0.45);
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.SUCCESS);
        g2.draw(new Arc2D.Double(x + 3, y + 3, SIZE - 6, SIZE - 6, 90, -360 * ring, Arc2D.OPEN));

        double fill = Math.max(0, Math.min(1, (t - 0.35) / 0.3));
        if (fill > 0) {
            double r = (SIZE - 6) / 2.0 * fill;
            g2.fill(new Ellipse2D.Double(x + SIZE / 2.0 - r, y + SIZE / 2.0 - r, r * 2, r * 2));
        }
        double tick = Math.max(0, Math.min(1, (t - 0.55) / 0.45));
        if (tick > 0) {
            double[][] pts = {{0.30, 0.52}, {0.44, 0.66}, {0.71, 0.38}};
            double first = 0.4;
            Path2D path = new Path2D.Double();
            path.moveTo(x + SIZE * pts[0][0], y + SIZE * pts[0][1]);
            double a = Math.min(1, tick / first);
            path.lineTo(x + SIZE * (pts[0][0] + (pts[1][0] - pts[0][0]) * a), y + SIZE * (pts[0][1] + (pts[1][1] - pts[0][1]) * a));
            if (tick > first) {
                double b = (tick - first) / (1 - first);
                path.lineTo(x + SIZE * (pts[1][0] + (pts[2][0] - pts[1][0]) * b), y + SIZE * (pts[1][1] + (pts[2][1] - pts[1][1]) * b));
            }
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(path);
        }
        g2.dispose();
    }
}
