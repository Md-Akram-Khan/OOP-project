package electricity.billing.system.ui.components;

import electricity.billing.system.model.UserAccount;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** The dark navigation rail: brand, menu with a sliding highlight, and the signed-in user. */
public class Sidebar extends JPanel {

    public static final int WIDTH = 260;

    private final JPanel nav = Ui.clear(null);
    private final List<NavItem> items = new ArrayList<>();
    private final Consumer<String> onNavigate;
    private NavItem selected;
    private double fromY;
    private double progress = 1;
    private Timer anim;

    public Sidebar(UserAccount account, Consumer<String> onNavigate, Runnable onToggleTheme, Runnable onLogout) {
        super(new BorderLayout());
        this.onNavigate = onNavigate;
        setOpaque(true);
        setBorder(new EmptyBorder(24, 16, 18, 16));

        Txt name = new Txt("Electricity", Theme.semibold(17), () -> Color.WHITE);
        Txt sub = new Txt("BILLING SYSTEM", Theme.semibold(10).deriveFont(Map.of(TextAttribute.TRACKING, 0.18)), () -> Theme.AQUA);
        IconButton theme = new IconButton(Theme.isDark() ? Glyph.SUN : Glyph.MOON, "Switch light / dark mode", true);
        theme.addActionListener(e -> {
            onToggleTheme.run();
            theme.setGlyph(Theme.isDark() ? Glyph.SUN : Glyph.MOON);
        });
        JPanel brand = Ui.clear(new BorderLayout());
        brand.add(Ui.hbox(12, IconTile.brand(Glyph.BOLT, 40), Ui.vbox(2, name, sub)), BorderLayout.WEST);
        JPanel toggle = Ui.clear(new CenterLayout());
        toggle.add(theme);
        brand.add(toggle, BorderLayout.EAST);
        brand.setBorder(new EmptyBorder(0, 6, 26, 0));
        add(brand, BorderLayout.NORTH);

        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        add(nav, BorderLayout.CENTER);

        IconButton logout = new IconButton(Glyph.LOGOUT, "Log out", true);
        logout.addActionListener(e -> onLogout.run());

        Txt user = new Txt(account.name() == null || account.name().isBlank() ? account.username() : account.name(),
                Theme.semibold(13), () -> Color.WHITE);
        Txt role = new Txt(account.isAdmin() ? "Administrator" : "Customer · " + account.meterNo(),
                Theme.regular(12), () -> Theme.INK_MUTED);
        JPanel who = Ui.vbox(2, user, role);
        JPanel footer = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 12));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16));
                g2.setColor(new Color(255, 255, 255, 18));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 16, 16));
                g2.dispose();
            }
        };
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 10, 10, 6));
        footer.add(new Avatar(account.name() == null ? account.username() : account.name(), 38), BorderLayout.WEST);
        footer.add(who, BorderLayout.CENTER);
        JPanel out = Ui.clear(new CenterLayout());
        out.add(logout);
        footer.add(out, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    public void section(String title) {
        Txt label = new Txt(title.toUpperCase(), Theme.semibold(10).deriveFont(Map.of(TextAttribute.TRACKING, 0.14)),
                () -> Theme.INK_MUTED);
        label.setBorder(new EmptyBorder(items.isEmpty() ? 0 : 18, 12, 8, 0));
        nav.add(Ui.left(label));
    }

    /** A page link that stays highlighted while its page is open. */
    public void item(String key, String label, Glyph glyph) {
        NavItem item = new NavItem(key, label, glyph, () -> onNavigate.accept(key));
        items.add(item);
        nav.add(Ui.left(item));
        nav.add(Box.createVerticalStrut(2));
    }

    public void select(String key) {
        NavItem next = items.stream().filter(i -> key.equals(i.key)).findFirst().orElse(null);
        if (next == null || next == selected) {
            return;
        }
        NavItem previous = selected;
        selected = next;
        items.forEach(i -> i.repaint());
        if (previous == null || !previous.isShowing()) {
            progress = 1;
            repaint();
            return;
        }
        fromY = boundsOf(previous).y;
        Anim.stop(anim);
        anim = Anim.run(300, t -> {
            progress = t;
            repaint();
        });
    }

    private Rectangle boundsOf(Component c) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, Theme.INK_TOP, 0, h, Theme.INK_BOTTOM));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), w * 1.1f, new float[] {0f, 1f},
                new Color[] {Theme.alpha(Theme.BLUE, 60), Theme.alpha(Theme.BLUE, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new RadialGradientPaint(new Point2D.Double(w, h), w * 0.9f, new float[] {0f, 1f},
                new Color[] {Theme.alpha(Theme.AQUA, 26), Theme.alpha(Theme.AQUA, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g2.fillRect(0, 0, w, h);

        if (selected != null && selected.getWidth() > 0) {
            Rectangle target = boundsOf(selected);
            double y = Anim.lerp(fromY, target.y, progress);
            RoundRectangle2D pill = new RoundRectangle2D.Double(target.x, y, target.width, target.height, 12, 12);
            g2.setPaint(new GradientPaint(target.x, 0, Theme.alpha(Theme.BLUE, 120), target.x + target.width, 0,
                    Theme.alpha(Theme.AQUA, 40)));
            g2.fill(pill);
            g2.setColor(new Color(255, 255, 255, 22));
            g2.draw(pill);
            g2.setColor(Theme.AQUA);
            g2.fill(new RoundRectangle2D.Double(target.x - 16, y + 10, 4, target.height - 20, 4, 4));
        }
        g2.dispose();
    }

    private final class NavItem extends JComponent {
        private final String key;
        private final String label;
        private final Glyph glyph;
        private double hover;
        private Timer anim;

        NavItem(String key, String label, Glyph glyph, Runnable onClick) {
            this.key = key;
            this.label = label;
            this.glyph = glyph;
            setFont(Theme.semibold(14));
            Ui.hand(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }

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
            return new Dimension(200, 44);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 44);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean active = this == selected;
            if (!active && hover > 0) {
                g2.setColor(new Color(255, 255, 255, (int) (hover * 14)));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
            }
            Color iconColor = active ? Theme.AQUA : Theme.mix(Theme.INK_MUTED, Theme.INK_TEXT, hover);
            Icons.paint(g2, glyph, 14, (getHeight() - 19) / 2.0, 19, iconColor, 2f);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(active ? Color.WHITE : Theme.mix(Theme.INK_TEXT, Color.WHITE, hover * 0.6));
            g2.drawString(label, 46, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
