package electricity.billing.system.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/**
 * The "Midnight Grid" design system: an electric blue to aqua brand gradient,
 * an amber spark accent and a deep navy sidebar, in light and dark modes.
 *
 * Colours are read through methods at paint time, so switching modes repaints
 * every custom component without rebuilding it.
 */
public final class Theme {

    // Brand
    public static final Color BLUE = new Color(0x2F6BFF);
    public static final Color AQUA = new Color(0x00C9B7);
    public static final Color AMBER = new Color(0xFFB224);
    public static final Color VIOLET = new Color(0x7A5CFF);

    // Status
    public static final Color SUCCESS = new Color(0x12B76A);
    public static final Color WARNING = new Color(0xF79009);
    public static final Color DANGER = new Color(0xF04438);

    // The sidebar and auth artwork are always dark ("ink"), in both modes.
    public static final Color INK_TOP = new Color(0x0A1024);
    public static final Color INK_BOTTOM = new Color(0x111A38);
    public static final Color INK_TEXT = new Color(0xC6CFE6);
    public static final Color INK_MUTED = new Color(0x6E7A98);

    public static final int RADIUS = 16;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Preferences PREFS = Preferences.userNodeForPackage(Theme.class);
    private static boolean dark;
    private static boolean fontsInstalled;

    private Theme() {
    }

    public static boolean isDark() {
        return dark;
    }

    public static Color bg() {
        return dark ? new Color(0x0B1120) : new Color(0xF3F5FA);
    }

    public static Color surface() {
        return dark ? new Color(0x131B2E) : Color.WHITE;
    }

    public static Color surfaceAlt() {
        return dark ? new Color(0x1A2440) : new Color(0xF6F8FC);
    }

    public static Color input() {
        return dark ? new Color(0x0F1729) : Color.WHITE;
    }

    public static Color border() {
        return dark ? new Color(0x24304D) : new Color(0xE4E8F1);
    }

    public static Color text() {
        return dark ? new Color(0xE8ECF6) : new Color(0x101828);
    }

    public static Color muted() {
        return dark ? new Color(0x8C96AD) : new Color(0x667085);
    }

    public static Color shadow() {
        return dark ? new Color(0, 0, 0) : new Color(16, 24, 40);
    }

    /** The brand gradient between two points. */
    public static GradientPaint brand(float x1, float y1, float x2, float y2) {
        return new GradientPaint(x1, y1, BLUE, x2, y2, AQUA);
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ---- Typography (Inter) ------------------------------------------------

    public static Font regular(float size) {
        return new Font(FlatInterFont.FAMILY, Font.PLAIN, 1).deriveFont(size);
    }

    public static Font semibold(float size) {
        return new Font(FlatInterFont.FAMILY_SEMIBOLD, Font.PLAIN, 1).deriveFont(size);
    }

    public static Font bold(float size) {
        return new Font(FlatInterFont.FAMILY, Font.BOLD, 1).deriveFont(size);
    }

    /**
     * A font that has Bangla letters (Inter doesn't), for names and addresses
     * from the NID records: Nirmala UI on Windows, else any installed font
     * that can show them.
     */
    public static Font bangla(float size) {
        if (banglaFamily == null) {
            banglaFamily = "Dialog";
            String sample = "নাম";
            for (String family : new String[] {"Nirmala UI", "Noto Sans Bengali", "Vrinda", "Shonar Bangla", "Kalpurush"}) {
                Font f = new Font(family, Font.PLAIN, 12);
                if (f.getFamily().equals(family) && f.canDisplayUpTo(sample) == -1) {
                    banglaFamily = family;
                    break;
                }
            }
        }
        return new Font(banglaFamily, Font.PLAIN, 1).deriveFont(size);
    }

    private static String banglaFamily;

    // ---- Installing and switching -----------------------------------------

    /** Installs the theme, using the mode the user picked last time. */
    public static void install() {
        apply(PREFS.getBoolean("dark", false));
    }

    public static void toggle() {
        apply(!dark);
        PREFS.putBoolean("dark", dark);
        FlatLaf.updateUI();
        LISTENERS.forEach(Runnable::run);
    }

    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    private static void apply(boolean darkMode) {
        dark = darkMode;
        if (!fontsInstalled) {
            FlatInterFont.install();
            FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
            FlatLaf.setPreferredLightFontFamily(FlatInterFont.FAMILY_LIGHT);
            FlatLaf.setPreferredSemiboldFontFamily(FlatInterFont.FAMILY_SEMIBOLD);
            fontsInstalled = true;
        }
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", hex(BLUE),
                "@background", hex(bg()),
                "@foreground", hex(text()),
                "@componentBackground", hex(input()),
                "@buttonBackground", hex(surface())));
        if (darkMode) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }

        put("defaultFont", new FontUIResource(regular(14)));
        put("Component.arc", 12);
        put("Button.arc", 12);
        put("TextComponent.arc", 12);
        put("CheckBox.arc", 6);
        put("ProgressBar.arc", 999);
        put("Component.focusWidth", 2);
        put("Component.innerFocusWidth", 0);
        put("Component.borderColor", color(border()));
        put("Component.disabledBorderColor", color(border()));
        put("Component.focusedBorderColor", color(BLUE));
        put("Component.focusColor", color(alpha(BLUE, dark ? 110 : 70)));

        for (String key : new String[] {"TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Spinner"}) {
            put(key + ".background", color(input()));
        }
        put("TextField.placeholderForeground", color(muted()));
        put("PasswordField.showRevealButton", true);
        put("PasswordField.showCapsLock", true);
        put("ComboBox.buttonBackground", color(input()));
        put("ComboBox.popupBackground", color(surface()));
        put("ComboBox.selectionBackground", color(alpha(BLUE, dark ? 90 : 40)));
        put("ComboBox.selectionForeground", color(text()));
        put("List.background", color(surface()));
        put("PopupMenu.background", color(surface()));

        put("Panel.background", color(bg()));
        put("RootPane.background", color(bg()));
        put("TitlePane.background", color(bg()));
        put("TitlePane.inactiveBackground", color(bg()));
        put("Label.foreground", color(text()));
        put("ScrollPane.background", color(bg()));
        put("Viewport.background", color(bg()));
        put("ScrollBar.width", 10);
        put("ScrollBar.thumbArc", 999);
        put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        put("ScrollBar.showButtons", false);
        put("ScrollBar.track", color(alpha(bg(), 0)));
        put("ScrollBar.thumb", color(alpha(muted(), dark ? 110 : 90)));

        put("Table.background", color(surface()));
        put("Table.foreground", color(text()));
        put("Table.alternateRowColor", color(dark ? new Color(0x16203A) : new Color(0xFAFBFE)));
        put("Table.selectionBackground", color(dark ? new Color(0x1E3366) : new Color(0xE6EEFF)));
        put("Table.selectionForeground", color(text()));
        put("Table.selectionInactiveBackground", color(dark ? new Color(0x1E3366) : new Color(0xE6EEFF)));
        put("Table.selectionInactiveForeground", color(text()));
        put("Table.gridColor", color(border()));
        put("Table.rowHeight", 48);
        put("Table.showHorizontalLines", true);
        put("Table.showVerticalLines", false);
        put("Table.intercellSpacing", new Dimension(0, 1));
        put("Table.cellFocusColor", color(alpha(BLUE, 0)));
        put("TableHeader.background", color(surfaceAlt()));
        put("TableHeader.foreground", color(muted()));
        put("TableHeader.font", new FontUIResource(semibold(12)));
        put("TableHeader.height", 42);
        put("TableHeader.separatorColor", color(alpha(border(), 0)));
        put("TableHeader.bottomSeparatorColor", color(border()));

        put("ToolTip.background", color(new Color(0x101828)));
        put("ToolTip.foreground", color(Color.WHITE));
        put("OptionPane.background", color(surface()));
        put("Panel.background", color(bg()));
    }

    private static void put(String key, Object value) {
        UIManager.put(key, value);
    }

    private static ColorUIResource color(Color c) {
        return new ColorUIResource(c);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
