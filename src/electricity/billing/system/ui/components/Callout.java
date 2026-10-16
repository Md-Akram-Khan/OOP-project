package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** A tinted box with an icon, a title and a few lines of text: warnings, tips and alerts. */
public class Callout extends JPanel {

    private Color color;
    private final JLabel icon = new JLabel();
    private final Txt title;
    private final JPanel lines = Ui.vbox(4);
    private final int wrap;

    public Callout(Glyph glyph, Color color, String title, int wrapWidth) {
        super(new BorderLayout(12, 0));
        this.color = color;
        this.wrap = wrapWidth;
        this.title = new Txt(title, Theme.semibold(14), Theme::text);
        setOpaque(false);
        setBorder(new EmptyBorder(14, 16, 14, 16));
        setGlyph(glyph);
        icon.setVerticalAlignment(JLabel.TOP);
        add(icon, BorderLayout.WEST);
        add(Ui.vbox(6, this.title, lines), BorderLayout.CENTER);
    }

    /** Changes the look, e.g. from a warning to an error. */
    public Callout look(Glyph glyph, Color newColor) {
        color = newColor;
        setGlyph(glyph);
        repaint();
        return this;
    }

    private void setGlyph(Glyph glyph) {
        icon.setIcon(Icons.of(glyph, 20, () -> Theme.mix(color, Theme.isDark() ? Color.WHITE : Color.BLACK, 0.15)));
    }

    public Callout title(String text) {
        title.setText(text);
        return this;
    }

    /** Replaces the body; each entry becomes its own wrapped line. */
    public Callout lines(List<String> texts) {
        lines.removeAll();
        for (String text : texts) {
            lines.add(Ui.left(Txt.muted(text).wrap(wrap)));
        }
        lines.setVisible(!texts.isEmpty());
        revalidate();
        repaint();
        return this;
    }

    public Callout lines(String... texts) {
        return lines(List.of(texts));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(Theme.alpha(color, Theme.isDark() ? 34 : 22));
        g2.fill(shape);
        g2.setColor(Theme.alpha(color, 90));
        g2.draw(shape);
        g2.dispose();
    }
}
