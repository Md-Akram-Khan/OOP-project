package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/** One line of text filled with a gradient. */
public class GradientText extends JComponent {

    private String text;
    private final Color from;
    private final Color to;

    public GradientText(String text, Font font) {
        this(text, font, Theme.BLUE, Theme.AQUA);
    }

    public GradientText(String text, Font font, Color from, Color to) {
        this.text = text;
        this.from = from;
        this.to = to;
        setFont(font);
    }

    public void setText(String newText) {
        text = newText;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 2, fm.getHeight());
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text);
        g2.setPaint(new GradientPaint(0, 0, from, Math.max(1, w), 0, to));
        g2.drawString(text, 0, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
