package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.DoubleFunction;
import javax.swing.JComponent;
import javax.swing.Timer;

/** A column chart whose bars grow in one after another, with a value bubble on hover. */
public class BarChart extends JComponent {

    public record Bar(String label, double value) {
    }

    private static final int LEFT = 70;
    private static final int RIGHT = 8;
    private static final int TOP = 34;
    private static final int BOTTOM = 30;

    private List<Bar> bars = List.of();
    private final DoubleFunction<String> axisFormat;
    private final DoubleFunction<String> valueFormat;
    private final String emptyText;
    private double progress = 1;
    private int hover = -1;
    private Timer anim;

    public BarChart(DoubleFunction<String> axisFormat, DoubleFunction<String> valueFormat, String emptyText) {
        this.axisFormat = axisFormat;
        this.valueFormat = valueFormat;
        this.emptyText = emptyText;
        setFont(Theme.regular(12));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = indexAt(e.getX());
                if (index != hover) {
                    hover = index;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setData(List<Bar> data) {
        bars = List.copyOf(data);
        hover = -1;
        Anim.stop(anim);
        anim = Anim.run(1100, t -> {
            progress = t;
            repaint();
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 260);
    }

    private int indexAt(int x) {
        if (bars.isEmpty()) {
            return -1;
        }
        double slot = (getWidth() - LEFT - RIGHT) / (double) bars.size();
        int index = (int) ((x - LEFT) / slot);
        return x < LEFT || index >= bars.size() ? -1 : index;
    }

    private static double niceCeiling(double value) {
        if (value <= 0) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        for (double step : new double[] {1, 2, 2.5, 4, 5, 10}) {
            if (step * magnitude >= value) {
                return step * magnitude;
            }
        }
        return 10 * magnitude;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int plotW = getWidth() - LEFT - RIGHT;
        int plotH = getHeight() - TOP - BOTTOM;
        double max = niceCeiling(bars.stream().mapToDouble(Bar::value).max().orElse(0));
        boolean empty = bars.stream().allMatch(b -> b.value() <= 0);

        // Grid lines and axis labels
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 4f}, 0f));
        for (int i = 0; i <= 4; i++) {
            double y = TOP + plotH - plotH * i / 4.0;
            g2.setColor(i == 0 ? Theme.border() : Theme.alpha(Theme.border(), 200));
            g2.draw(new Line2D.Double(LEFT, y, LEFT + plotW, y));
            if (!empty) {
                String label = axisFormat.apply(max * i / 4.0);
                g2.setColor(Theme.muted());
                g2.drawString(label, LEFT - 12 - fm.stringWidth(label), (float) (y + fm.getAscent() / 2.0 - 1));
            }
        }

        if (empty) {
            g2.setColor(Theme.muted());
            g2.drawString(emptyText, LEFT + (plotW - fm.stringWidth(emptyText)) / 2f, TOP + plotH / 2f);
        }

        double slot = bars.isEmpty() ? 0 : plotW / (double) bars.size();
        double barW = Math.min(46, slot * 0.52);
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            double cx = LEFT + slot * i + slot / 2;
            // Each bar starts a little after the previous one.
            double local = Math.max(0, Math.min(1, progress * 1.6 - i * 0.1));
            local = Anim.easeOut(local);
            double h = max <= 0 ? 0 : bar.value() / max * plotH * local;
            double x = cx - barW / 2;
            double y = TOP + plotH - h;

            if (h > 0.5) {
                Area shape = new Area(new RoundRectangle2D.Double(x, y, barW, h, 12, 12));
                shape.add(new Area(new Rectangle2D.Double(x, y + Math.min(h, 6), barW, h - Math.min(h, 6))));
                boolean dim = hover >= 0 && hover != i;
                Color top = dim ? Theme.alpha(Theme.AQUA, 110) : Theme.AQUA;
                Color bottom = dim ? Theme.alpha(Theme.BLUE, 110) : Theme.BLUE;
                g2.setPaint(new GradientPaint(0, (float) y, top, 0, TOP + plotH, bottom));
                g2.fill(shape);
            }

            g2.setColor(hover == i ? Theme.text() : Theme.muted());
            g2.drawString(bar.label(), (float) (cx - fm.stringWidth(bar.label()) / 2.0), getHeight() - 8);

            if (hover == i) {
                String text = valueFormat.apply(bar.value());
                g2.setFont(Theme.semibold(12));
                FontMetrics bold = g2.getFontMetrics();
                double bw = bold.stringWidth(text) + 20;
                double bx = Math.max(2, Math.min(getWidth() - bw - 2, cx - bw / 2));
                double by = Math.max(2, y - 34);
                g2.setColor(Theme.isDark() ? new Color(0xE8ECF6) : new Color(0x101828));
                g2.fill(new RoundRectangle2D.Double(bx, by, bw, 26, 10, 10));
                g2.setColor(Theme.isDark() ? new Color(0x101828) : Color.WHITE);
                g2.drawString(text, (float) (bx + 10), (float) (by + 13 + bold.getAscent() / 2.0 - 2));
                g2.setFont(getFont());
            }
        }
        g2.dispose();
    }
}
