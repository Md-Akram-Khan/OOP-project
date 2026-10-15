package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** A clickable option tile; the selected one gets a brand outline and a check mark. */
public class ChoiceCard extends JPanel {

    private boolean selected;
    private double hover;
    private double pick;
    private Timer hoverAnim;
    private Timer pickAnim;

    public ChoiceCard(Component content, Runnable onClick) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(16, 16, 16, 40));
        add(content, BorderLayout.CENTER);
        Ui.hand(this);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                animateHover(1);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!contains(e.getPoint())) {
                    animateHover(0);
                }
            }
        };
        addMouseListener(mouse);
    }

    /** A standard layout: icon tile, title and one line of description. */
    public static ChoiceCard simple(Glyph glyph, String title, String description, Runnable onClick) {
        return new ChoiceCard(Ui.hbox(14, new IconTile(glyph, 42, Theme.BLUE),
                Ui.vbox(4, Txt.h3(title), Txt.small(description))), onClick);
    }

    public void setSelected(boolean value) {
        if (value == selected) {
            return;
        }
        selected = value;
        Anim.stop(pickAnim);
        double from = pick;
        double to = value ? 1 : 0;
        pickAnim = Anim.run(220, t -> {
            pick = Anim.lerp(from, to, t);
            repaint();
        });
    }

    public boolean isSelected() {
        return selected;
    }

    private void animateHover(double to) {
        Anim.stop(hoverAnim);
        double from = hover;
        hoverAnim = Anim.run(160, t -> {
            hover = Anim.lerp(from, to, t);
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        RoundRectangle2D shape = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 16, 16);
        g2.setColor(Theme.mix(Theme.surface(), Theme.BLUE, pick * (Theme.isDark() ? 0.12 : 0.05) + hover * 0.02));
        g2.fill(shape);
        Color edge = Theme.mix(Theme.mix(Theme.border(), Theme.BLUE, hover * 0.35), Theme.BLUE, pick);
        g2.setColor(edge);
        g2.setStroke(new java.awt.BasicStroke((float) (1 + pick)));
        g2.draw(shape);
        if (pick > 0.01) {
            double r = 10 * pick;
            double cx = w - 24;
            double cy = 24;
            g2.setPaint(Theme.brand((float) (cx - r), (float) (cy - r), (float) (cx + r), (float) (cy + r)));
            g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            Icons.paint(g2, Glyph.CHECK, cx - 6.5 * pick, cy - 6.5 * pick, 13 * pick, Color.WHITE, 3f);
        }
        g2.dispose();
    }
}
