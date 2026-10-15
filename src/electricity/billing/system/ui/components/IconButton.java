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
import javax.swing.Timer;

/** A compact square icon-only button; {@code onInk} styles it for the dark sidebar. */
public class IconButton extends JButton {

    private final boolean onInk;
    private Glyph glyph;
    private double hover;
    private Timer anim;

    public IconButton(Glyph glyph, String tooltip, boolean onInk) {
        this.onInk = onInk;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setToolTipText(tooltip);
        Ui.hand(this);
        setGlyph(glyph);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                animate(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                animate(0);
            }
        });
    }

    public void setGlyph(Glyph glyph) {
        this.glyph = glyph;
        setIcon(Icons.of(glyph, 18, () -> onInk
                ? Theme.mix(Theme.INK_TEXT, Color.WHITE, hover)
                : Theme.mix(Theme.muted(), Theme.text(), hover)));
        repaint();
    }

    public Glyph glyph() {
        return glyph;
    }

    private void animate(double to) {
        Anim.stop(anim);
        double from = hover;
        anim = Anim.run(160, t -> {
            hover = Anim.lerp(from, to, t);
            repaint();
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(36, 36);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = onInk ? Color.WHITE : Theme.text();
        g2.setColor(Theme.alpha(base, (int) (hover * (onInk ? 22 : 16))));
        g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
        g2.dispose();
        super.paintComponent(g);
    }
}
