package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** A spinning gradient arc for "waiting" states. Animates only while on screen. */
public class Spinner extends JComponent {

    private final int size;
    private final long start = System.nanoTime();
    private final Timer timer = new Timer(16, e -> repaint());

    public Spinner(int size) {
        this.size = size;
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
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double t = (System.nanoTime() - start) / 1e9;
        double x = (getWidth() - size) / 2.0 + 4;
        double y = (getHeight() - size) / 2.0 + 4;
        double d = size - 8;
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.alpha(Theme.BLUE, Theme.isDark() ? 60 : 35));
        g2.draw(new Ellipse2D.Double(x, y, d, d));
        double startAngle = -(t * 300) % 360;
        double sweep = 70 + 60 * (1 + Math.sin(t * 3)) / 2;
        g2.setPaint(Theme.brand((float) x, (float) y, (float) (x + d), (float) (y + d)));
        g2.draw(new Arc2D.Double(x, y, d, d, startAngle, sweep, Arc2D.OPEN));
        g2.dispose();
    }
}
