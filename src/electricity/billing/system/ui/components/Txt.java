package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JLabel;

/** A label whose colour follows the theme, with presets for each text style. */
public class Txt extends JLabel {

    private final Supplier<Color> color;
    private int wrapWidth;
    private String plainText;

    public Txt(String text, Font font, Supplier<Color> color) {
        super(text);
        this.color = color;
        this.plainText = text;
        setFont(font);
    }

    public static Txt display(String text) {
        return new Txt(text, Theme.semibold(30), Theme::text);
    }

    public static Txt h1(String text) {
        return new Txt(text, Theme.semibold(26), Theme::text);
    }

    public static Txt h2(String text) {
        return new Txt(text, Theme.semibold(18), Theme::text);
    }

    public static Txt h3(String text) {
        return new Txt(text, Theme.semibold(15), Theme::text);
    }

    public static Txt body(String text) {
        return new Txt(text, Theme.regular(14), Theme::text);
    }

    public static Txt muted(String text) {
        return new Txt(text, Theme.regular(14), Theme::muted);
    }

    public static Txt small(String text) {
        return new Txt(text, Theme.regular(12), Theme::muted);
    }

    /** Small spaced-out capitals, for section labels. */
    public static Txt caps(String text) {
        Font font = Theme.semibold(11).deriveFont(Map.of(TextAttribute.TRACKING, 0.09));
        return new Txt(text.toUpperCase(Locale.ROOT), font, Theme::muted);
    }

    public Txt color(Supplier<Color> c) {
        return new Txt(plainText, getFont(), c).wrap(wrapWidth);
    }

    /** Wraps the text onto several lines at the given width (0 = no wrapping). */
    public Txt wrap(int width) {
        wrapWidth = width;
        setText(plainText);
        return this;
    }

    @Override
    public void setText(String text) {
        plainText = text;
        if (wrapWidth > 0 && text != null) {
            int cssWidth = (int) Math.round(wrapWidth / htmlScale());
            super.setText("<html><div style='width:" + cssWidth + "px'>" + escape(text) + "</div></html>");
        } else {
            super.setText(text);
        }
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        java.awt.Dimension d = super.getPreferredSize();
        d.width += 3;
        Object tracking = getFont().getAttributes().get(TextAttribute.TRACKING);
        if (tracking instanceof Number n && plainText != null && wrapWidth == 0) {
            d.width += (int) Math.ceil(n.doubleValue() * getFont().getSize2D() * plainText.length()) + 2;
        }
        return d;
    }

    @Override
    public java.awt.Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Color getForeground() {
        return color == null ? super.getForeground() : color.get();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // HTML text bakes in its colour when set; set it again so a theme switch recolours it.
        if (wrapWidth > 0) {
            setText(plainText);
        }
    }

    private static double htmlScale;

    /**
     * Swing's HTML renderer scales CSS pixel widths (by about 1.3 at normal
     * DPI). Measure it once so wrapped text fits the width we actually asked for.
     */
    private static double htmlScale() {
        if (htmlScale == 0) {
            JLabel probe = new JLabel("<html><div style='width:100px'>" + "word ".repeat(80) + "</div></html>");
            probe.setFont(Theme.regular(14));
            double measured = probe.getPreferredSize().width / 100.0;
            htmlScale = measured > 0.5 && measured < 3 ? measured : 1;
        }
        return htmlScale;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
