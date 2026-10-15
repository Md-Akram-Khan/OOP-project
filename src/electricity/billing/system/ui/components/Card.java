package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Function;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * A rounded surface with a soft shadow. Hoverable cards glow and lift a little
 * when the mouse is over them or any of their children.
 */
public class Card extends JPanel {

    /** Space reserved around the card for its shadow. */
    public static final int SHADOW = 10;
    private static final int ARC = 18;
    private static Card hovered;
    private static boolean trackerInstalled;

    private Function<Rectangle, Paint> fill;
    private boolean hoverable;
    private double hover;
    private Timer anim;

    public Card() {
        this(new BorderLayout());
    }

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        pad(20, 22, 20, 22);
    }

    /** Inner padding, not counting the shadow margin. */
    public Card pad(int top, int left, int bottom, int right) {
        setBorder(new EmptyBorder(SHADOW + top, SHADOW + left, SHADOW + bottom, SHADOW + right));
        return this;
    }

    public Card hoverable() {
        hoverable = true;
        installTracker();
        return this;
    }

    /** Paints the card with a custom fill (e.g. a gradient) instead of the surface colour. */
    public Card fill(Function<Rectangle, Paint> fill) {
        this.fill = fill;
        return this;
    }

    private void setHover(boolean on) {
        Anim.stop(anim);
        double from = hover;
        double to = on ? 1 : 0;
        anim = Anim.run(220, t -> {
            hover = Anim.lerp(from, to, t);
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int lift = (int) Math.round(hover * 2);
        Rectangle body = new Rectangle(SHADOW, SHADOW - lift, getWidth() - SHADOW * 2, getHeight() - SHADOW * 2);

        paintShadow(g2, body, hover);

        RoundRectangle2D shape = new RoundRectangle2D.Double(body.x, body.y, body.width, body.height, ARC, ARC);
        g2.setPaint(fill != null ? fill.apply(body) : Theme.surface());
        g2.fill(shape);
        if (fill == null) {
            Color edge = Theme.mix(Theme.border(), Theme.BLUE, hover * 0.55);
            g2.setColor(edge);
            g2.draw(new RoundRectangle2D.Double(body.x + 0.5, body.y + 0.5, body.width - 1, body.height - 1, ARC, ARC));
        }
        g2.dispose();
    }

    /** A soft, layered drop shadow under a rounded rectangle. */
    static void paintShadow(Graphics2D g2, Rectangle body, double hover) {
        Color base = Theme.shadow();
        int strength = Theme.isDark() ? 26 : 7;
        strength += (int) (hover * (Theme.isDark() ? 10 : 5));
        int offsetY = 3 + (int) (hover * 3);
        for (int i = SHADOW; i >= 1; i--) {
            double falloff = 1 - (double) i / (SHADOW + 1);
            g2.setColor(Theme.alpha(base, (int) (strength * falloff * falloff)));
            g2.fill(new RoundRectangle2D.Double(body.x - i + 1, body.y - i + 1 + offsetY,
                    body.width + i * 2 - 2, body.height + i * 2 - 2, ARC + i * 2, ARC + i * 2));
        }
    }

    /** One global mouse listener decides which hoverable card is under the pointer. */
    private static void installTracker() {
        if (trackerInstalled) {
            return;
        }
        trackerInstalled = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent e)) {
                return;
            }
            int id = e.getID();
            if (id != MouseEvent.MOUSE_MOVED && id != MouseEvent.MOUSE_ENTERED && id != MouseEvent.MOUSE_EXITED) {
                return;
            }
            Card target = hoverableAncestor(e.getComponent());
            if (id == MouseEvent.MOUSE_EXITED && target != null) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), target);
                if (!target.contains(p)) {
                    target = null;
                }
            }
            if (target != hovered) {
                if (hovered != null) {
                    hovered.setHover(false);
                }
                hovered = target;
                if (target != null) {
                    target.setHover(true);
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private static Card hoverableAncestor(Component c) {
        Card found = null;
        for (Component p = c; p != null; p = p.getParent()) {
            if (p instanceof Card card && card.hoverable) {
                found = card;
                break;
            }
        }
        return found;
    }
}
