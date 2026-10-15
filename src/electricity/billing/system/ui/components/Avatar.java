package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import javax.swing.JComponent;

/** A round avatar showing initials on a gradient picked from the name, so each person keeps one colour. */
public class Avatar extends JComponent {

    private static final Color[][] GRADIENTS = {
        {Theme.BLUE, Theme.AQUA},
        {new Color(0x7A5CFF), new Color(0x2F6BFF)},
        {new Color(0xFF8A3D), Theme.AMBER},
        {new Color(0x00B37E), Theme.AQUA},
        {new Color(0xE0457B), new Color(0xFF8A3D)},
        {new Color(0x3D8BFF), new Color(0x7A5CFF)},
    };

    private final int size;
    private String name;

    public Avatar(String name, int size) {
        this.name = name;
        this.size = size;
        setFont(Theme.semibold(size * 0.38f));
    }

    public void setName(String newName) {
        name = newName;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color[] colors = GRADIENTS[Math.floorMod(name == null ? 0 : name.hashCode(), GRADIENTS.length)];
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        g2.setPaint(new GradientPaint(x, y, colors[0], x + size, y + size, colors[1]));
        g2.fill(new Ellipse2D.Double(x, y, size, size));
        String initials = Fmt.initials(name);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(initials, x + (size - fm.stringWidth(initials)) / 2f,
                y + (size - fm.getHeight()) / 2f + fm.getAscent());
        g2.dispose();
    }
}
