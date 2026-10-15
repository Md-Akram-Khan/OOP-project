package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

/** Layout shortcuts used by every page. */
public final class Ui {

    private Ui() {
    }

    /** A transparent panel. */
    public static JPanel clear(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /** An opaque panel whose background follows the theme. */
    public static JPanel themed(LayoutManager layout, Supplier<Color> background) {
        JPanel panel = new JPanel(layout) {
            @Override
            public Color getBackground() {
                return background == null ? super.getBackground() : background.get();
            }
        };
        panel.setOpaque(true);
        return panel;
    }

    /** Stacks components top to bottom, left-aligned, with a gap between each. */
    public static JPanel vbox(int gap, Component... children) {
        JPanel panel = clear(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < children.length; i++) {
            if (i > 0 && gap > 0) {
                panel.add(Box.createVerticalStrut(gap));
            }
            left(children[i]);
            panel.add(children[i]);
        }
        return panel;
    }

    /** Lines components up left to right, vertically centred, with a gap between each. */
    public static JPanel hbox(int gap, Component... children) {
        JPanel panel = clear(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        for (int i = 0; i < children.length; i++) {
            if (i > 0 && gap > 0) {
                panel.add(Box.createHorizontalStrut(gap));
            }
            if (children[i] instanceof JComponent c) {
                c.setAlignmentY(Component.CENTER_ALIGNMENT);
            }
            panel.add(children[i]);
        }
        return panel;
    }

    /** Equal-width columns. */
    public static JPanel columns(int gap, Component... children) {
        JPanel panel = clear(new GridLayout(1, children.length, gap, 0));
        for (Component c : children) {
            panel.add(c);
        }
        return panel;
    }

    /** A grid with the given number of columns, filled row by row. */
    public static JPanel grid(int cols, int hgap, int vgap, Component... children) {
        JPanel panel = clear(new GridLayout(0, cols, hgap, vgap));
        for (Component c : children) {
            panel.add(c);
        }
        return panel;
    }

    public static Component glue() {
        return Box.createHorizontalGlue();
    }

    public static Component vglue() {
        return Box.createVerticalGlue();
    }

    public static <T extends JComponent> T pad(T c, int top, int left, int bottom, int right) {
        c.setBorder(new EmptyBorder(top, left, bottom, right));
        return c;
    }

    public static <T extends Component> T left(T c) {
        if (c instanceof JComponent j) {
            j.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        return c;
    }

    /** Fixes a component's width while its height still follows its content. */
    public static JPanel fixedWidth(JComponent c, int width) {
        JPanel wrapper = new JPanel(new java.awt.BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, c.getPreferredSize().height);
            }
        };
        wrapper.setOpaque(false);
        wrapper.add(c, java.awt.BorderLayout.CENTER);
        return wrapper;
    }

    /** Keeps a component at its natural height at the top of the available space. */
    public static JPanel top(JComponent c) {
        JPanel wrapper = clear(new java.awt.BorderLayout());
        wrapper.add(c, java.awt.BorderLayout.NORTH);
        return wrapper;
    }

    /** Stops BoxLayout stretching a component taller than it wants to be. */
    public static <T extends JComponent> T fixedHeight(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    public static <T extends JComponent> T width(T c, int width) {
        Dimension pref = c.getPreferredSize();
        c.setPreferredSize(new Dimension(width, pref.height));
        c.setMaximumSize(new Dimension(width, pref.height));
        return c;
    }

    public static void hand(Component c) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** A borderless, transparent scroll pane that scrolls at a comfortable speed. */
    public static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    /**
     * Centres a component; if the window is too small to show it whole, it
     * scrolls instead of squashing.
     */
    public static JScrollPane centered(Component content) {
        class Center extends JPanel implements javax.swing.Scrollable {
            Center() {
                super(new CenterLayout());
                setOpaque(false);
                add(content);
            }

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return getPreferredSize();
            }

            @Override
            public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
                return 18;
            }

            @Override
            public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
                return 120;
            }

            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getParent() instanceof javax.swing.JViewport v && v.getWidth() >= getPreferredSize().width;
            }

            @Override
            public boolean getScrollableTracksViewportHeight() {
                return getParent() instanceof javax.swing.JViewport v && v.getHeight() >= getPreferredSize().height;
            }
        }
        JScrollPane scroll = scroll(new Center());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        return scroll;
    }

    public static Color surfaceHover() {
        return Theme.mix(Theme.surface(), Theme.BLUE, Theme.isDark() ? 0.10 : 0.05);
    }
}
