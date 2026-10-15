package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.Timer;

/** A pill-shaped switch between options; the gradient thumb slides to the chosen one. */
public class Segmented extends JComponent {

    private static final int HEIGHT = 42;
    private static final int PAD = 4;

    private final List<String> options = new ArrayList<>();
    private final List<IntConsumer> listeners = new ArrayList<>();
    private int selected;
    private double thumb;
    private int hover = -1;
    private Timer anim;

    public Segmented(String... options) {
        setFont(Theme.semibold(13));
        setOptions(List.of(options));
        Ui.hand(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i >= 0 && isEnabled()) {
                    select(i);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i != hover) {
                    hover = i;
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

    public final void setOptions(List<String> newOptions) {
        options.clear();
        options.addAll(newOptions);
        selected = Math.min(selected, options.size() - 1);
        thumb = selected;
        revalidate();
        repaint();
    }

    public List<String> options() {
        return List.copyOf(options);
    }

    public void onChange(IntConsumer listener) {
        listeners.add(listener);
    }

    public int selectedIndex() {
        return selected;
    }

    public String selectedOption() {
        return options.get(selected);
    }

    public void select(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        Anim.stop(anim);
        double from = thumb;
        anim = Anim.run(260, t -> {
            thumb = Anim.lerp(from, index, t);
            repaint();
        });
        listeners.forEach(l -> l.accept(index));
    }

    private int indexAt(int x) {
        if (options.isEmpty()) {
            return -1;
        }
        double seg = (getWidth() - PAD * 2) / (double) options.size();
        int i = (int) ((x - PAD) / seg);
        return i < 0 || i >= options.size() ? -1 : i;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int widest = options.stream().mapToInt(fm::stringWidth).max().orElse(40);
        return new Dimension((widest + 36) * Math.max(1, options.size()) + PAD * 2, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(Theme.isDark() ? new Color(0x0F1729) : new Color(0xEEF1F7));
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
        if (options.isEmpty()) {
            g2.dispose();
            return;
        }
        double seg = (w - PAD * 2) / (double) options.size();
        double tx = PAD + seg * thumb;
        g2.setPaint(Theme.brand((float) tx, 0, (float) (tx + seg), h));
        g2.fill(new RoundRectangle2D.Double(tx, PAD, seg, h - PAD * 2, h - PAD * 2, h - PAD * 2));

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < options.size(); i++) {
            String label = options.get(i);
            double closeness = Math.max(0, 1 - Math.abs(thumb - i));
            Color idle = hover == i ? Theme.text() : Theme.muted();
            g2.setColor(Theme.mix(idle, Color.WHITE, closeness));
            float x = (float) (PAD + seg * i + (seg - fm.stringWidth(label)) / 2);
            g2.drawString(label, x, (h - fm.getHeight()) / 2f + fm.getAscent());
        }
        g2.dispose();
    }
}
