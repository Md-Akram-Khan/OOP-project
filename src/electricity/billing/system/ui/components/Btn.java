package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Buttons in five styles; the primary one carries the brand gradient and glows on hover. */
public class Btn extends JButton {

    public enum Style { PRIMARY, SECONDARY, GHOST, DANGER, SUCCESS }

    private static final int ARC = 12;
    private static final int HEIGHT = 42;

    private final Style style;
    private double hover;
    private boolean pressed;
    private Timer anim;
    private String idleText;
    private boolean fullWidth;

    public Btn(String text, Style style) {
        this(text, null, style);
    }

    public Btn(String text, Glyph glyph, Style style) {
        super(text);
        this.style = style;
        setFont(Theme.semibold(14));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setBorder(new EmptyBorder(0, 18, 0, 18));
        setIconTextGap(8);
        Ui.hand(this);
        if (glyph != null) {
            setIcon(Icons.of(glyph, 17, this::getForeground));
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                animateHover(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                animateHover(0);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
            }
        });
    }

    /** Puts the icon after the text, e.g. for "Continue →". */
    public Btn trailingIcon() {
        setHorizontalTextPosition(SwingConstants.LEADING);
        return this;
    }

    /** Lets the button stretch to fill the width of a vertical stack. */
    public Btn fullWidth() {
        fullWidth = true;
        return this;
    }

    /** Shows a working state (disabled, different text) while a request runs. */
    public void setBusy(boolean busy, String busyText) {
        if (busy) {
            idleText = getText();
            setText(busyText);
        } else if (idleText != null) {
            setText(idleText);
        }
        setEnabled(!busy);
    }

    private void animateHover(double to) {
        Anim.stop(anim);
        double from = hover;
        anim = Anim.run(180, t -> {
            hover = Anim.lerp(from, to, t);
            repaint();
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height, HEIGHT));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(fullWidth ? Integer.MAX_VALUE : getPreferredSize().width, getPreferredSize().height);
    }

    @Override
    public Color getForeground() {
        if (style == null) {
            return super.getForeground();
        }
        return switch (style) {
            case PRIMARY, DANGER, SUCCESS -> Color.WHITE;
            case SECONDARY, GHOST -> isEnabled() ? Theme.text() : Theme.muted();
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        double glow = isEnabled() ? hover : 0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, h, ARC, ARC);

        switch (style) {
            case PRIMARY -> {
                if (glow > 0) {
                    for (int i = 6; i >= 1; i--) {
                        g2.setColor(Theme.alpha(Theme.BLUE, (int) (glow * 14 * (1 - i / 7.0))));
                        g2.fill(new RoundRectangle2D.Double(-i, -i + 3, w + i * 2, h + i * 2, ARC + i * 2, ARC + i * 2));
                    }
                }
                g2.setPaint(isEnabled() ? Theme.brand(0, 0, w, h) : new java.awt.GradientPaint(0, 0,
                        Theme.alpha(Theme.BLUE, 110), w, h, Theme.alpha(Theme.AQUA, 110)));
                g2.fill(shape);
                g2.setColor(new Color(255, 255, 255, (int) (glow * 28)));
                g2.fill(shape);
            }
            case SECONDARY -> {
                g2.setColor(Theme.mix(Theme.surface(), Theme.isDark() ? Color.WHITE : Theme.BLUE, glow * 0.06));
                g2.fill(shape);
                g2.setColor(Theme.mix(Theme.border(), Theme.BLUE, glow * 0.5));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, ARC, ARC));
            }
            case GHOST -> {
                g2.setColor(Theme.alpha(Theme.text(), (int) (glow * 18)));
                g2.fill(shape);
            }
            case DANGER, SUCCESS -> {
                Color base = style == Style.DANGER ? Theme.DANGER : Theme.SUCCESS;
                g2.setColor(isEnabled() ? Theme.mix(base, Color.WHITE, glow * 0.12) : Theme.alpha(base, 120));
                g2.fill(shape);
            }
            default -> { }
        }
        if (pressed && isEnabled()) {
            g2.setColor(new Color(0, 0, 0, 30));
            g2.fill(shape);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
