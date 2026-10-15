package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Numbered steps joined by a line that fills with the brand gradient as you progress. */
public class Stepper extends JComponent {

    private final String[] steps;
    private int current;
    private double shown;
    private Timer anim;

    public Stepper(String... steps) {
        this.steps = steps;
    }

    public void setStep(int step) {
        current = step;
        Anim.stop(anim);
        double from = shown;
        anim = Anim.run(420, t -> {
            shown = Anim.lerp(from, step, t);
            repaint();
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(700, 64);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, 64);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int n = steps.length;
        double left = 70;
        double right = Math.min(getWidth() - 70, 640);
        double gap = (right - left) / (n - 1);
        double cy = 18;

        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Theme.border());
        g2.draw(new Line2D.Double(left, cy, right, cy));
        g2.setPaint(Theme.brand((float) left, 0, (float) right, 0));
        g2.draw(new Line2D.Double(left, cy, left + gap * shown, cy));

        g2.setFont(Theme.semibold(13));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < n; i++) {
            double cx = left + gap * i;
            double reached = Math.max(0, Math.min(1, shown - i + 1));
            g2.setColor(Theme.surface());
            g2.fill(new Ellipse2D.Double(cx - 16, cy - 16, 32, 32));
            if (i < current || (i == n - 1 && current == n - 1)) {
                g2.setPaint(Theme.brand((float) (cx - 16), 0, (float) (cx + 16), 0));
                g2.fill(new Ellipse2D.Double(cx - 16, cy - 16, 32, 32));
                Icons.paint(g2, Glyph.CHECK, cx - 8, cy - 8, 16, Color.WHITE, 3f);
            } else {
                boolean active = i == current;
                g2.setColor(active ? Theme.BLUE : Theme.border());
                g2.setStroke(new BasicStroke(active ? 2.5f : 2f));
                g2.draw(new Ellipse2D.Double(cx - 15, cy - 15, 30, 30));
                String num = String.valueOf(i + 1);
                g2.setColor(active ? Theme.BLUE : Theme.muted());
                g2.drawString(num, (float) (cx - fm.stringWidth(num) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 2));
            }
            g2.setColor(reached > 0.5 ? Theme.text() : Theme.muted());
            g2.drawString(steps[i], (float) (cx - fm.stringWidth(steps[i]) / 2.0), (float) (cy + 40));
        }
        g2.dispose();
    }
}
