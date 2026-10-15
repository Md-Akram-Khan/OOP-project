package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/** An icon on a rounded tile: tinted, or filled with the brand gradient. */
public class IconTile extends JComponent {

    private final Glyph glyph;
    private final int size;
    private final Color color;
    private final boolean gradient;

    public IconTile(Glyph glyph, int size, Color color) {
        this(glyph, size, color, false);
    }

    public IconTile(Glyph glyph, int size, Color color, boolean gradient) {
        this.glyph = glyph;
        this.size = size;
        this.color = color;
        this.gradient = gradient;
    }

    public static IconTile brand(Glyph glyph, int size) {
        return new IconTile(glyph, size, Theme.BLUE, true);
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
        int x = (getWidth() - size) / 2;
        int y = (getHeight() - size) / 2;
        RoundRectangle2D tile = new RoundRectangle2D.Double(x, y, size, size, size * 0.36, size * 0.36);
        Color iconColor;
        if (gradient) {
            g2.setPaint(new GradientPaint(x, y, Theme.BLUE, x + size, y + size, Theme.AQUA));
            g2.fill(tile);
            iconColor = Color.WHITE;
        } else {
            g2.setColor(Theme.alpha(color, Theme.isDark() ? 50 : 30));
            g2.fill(tile);
            iconColor = Theme.isDark() ? Theme.mix(color, Color.WHITE, 0.2) : color;
        }
        double icon = size * 0.5;
        Icons.paint(g2, glyph, x + (size - icon) / 2, y + (size - icon) / 2, icon, iconColor, 2f);
        g2.dispose();
    }
}
