package electricity.billing.system.ui.components;

import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Notifications that slide in at the top right of the window and fade away on their own. */
public final class Toast {

    private enum Kind {
        SUCCESS(Glyph.CHECK_CIRCLE, Theme.SUCCESS, 3200),
        ERROR(Glyph.ALERT, Theme.DANGER, 6500),
        INFO(Glyph.INFO, Theme.BLUE, 3600);

        final Glyph glyph;
        final Color color;
        final int millis;

        Kind(Glyph glyph, Color color, int millis) {
            this.glyph = glyph;
            this.color = color;
            this.millis = millis;
        }
    }

    private static final int WIDTH = 360;
    private static final int MARGIN = 22;
    private static final Map<JLayeredPane, List<ToastPanel>> STACKS = new WeakHashMap<>();

    private Toast() {
    }

    public static void success(Component anchor, String message) {
        show(anchor, Kind.SUCCESS, message);
    }

    public static void error(Component anchor, String message) {
        show(anchor, Kind.ERROR, message);
    }

    public static void info(Component anchor, String message) {
        show(anchor, Kind.INFO, message);
    }

    private static void show(Component anchor, Kind kind, String message) {
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        JLayeredPane layers = root.getLayeredPane();
        ToastPanel toast = new ToastPanel(kind, message);
        List<ToastPanel> stack = STACKS.computeIfAbsent(layers, k -> new ArrayList<>());
        stack.add(toast);
        layers.add(toast, JLayeredPane.POPUP_LAYER);
        arrange(layers, stack);
        toast.enter(() -> {
            Timer wait = new Timer(kind.millis, e -> toast.leave(() -> {
                layers.remove(toast);
                stack.remove(toast);
                arrange(layers, stack);
                layers.repaint();
            }));
            wait.setRepeats(false);
            wait.start();
        });
    }

    private static void arrange(JLayeredPane layers, List<ToastPanel> stack) {
        int y = MARGIN;
        for (ToastPanel t : stack) {
            Dimension d = t.getPreferredSize();
            t.setBounds(layers.getWidth() - d.width - MARGIN, y, d.width, d.height);
            t.validate();
            y += d.height + 10;
        }
    }

    private static final class ToastPanel extends JPanel {
        private final Kind kind;
        private float alpha;
        private int slide = 40;
        private boolean leaving;

        ToastPanel(Kind kind, String message) {
            super(new BorderLayout(12, 0));
            this.kind = kind;
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 14, 16));
            JLabel icon = new JLabel(Icons.of(kind.glyph, 20, kind.color));
            icon.setVerticalAlignment(JLabel.TOP);
            add(icon, BorderLayout.WEST);
            add(Txt.body(message).wrap(WIDTH - 90), BorderLayout.CENTER);
            JLabel close = new JLabel(Icons.of(Glyph.X, 16, Theme::muted));
            close.setVerticalAlignment(JLabel.TOP);
            add(close, BorderLayout.EAST);
            Ui.hand(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    leave(() -> {
                        Component parent = getParent();
                        if (parent instanceof JLayeredPane layers) {
                            layers.remove(ToastPanel.this);
                            List<ToastPanel> stack = STACKS.get(layers);
                            if (stack != null) {
                                stack.remove(ToastPanel.this);
                                arrange(layers, stack);
                            }
                            layers.repaint();
                        }
                    });
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(WIDTH, super.getPreferredSize().height);
        }

        void enter(Runnable done) {
            Anim.run(320, t -> {
                alpha = (float) t;
                slide = (int) Math.round(40 * (1 - t));
                repaint();
            }, done);
        }

        void leave(Runnable done) {
            if (leaving) {
                return;
            }
            leaving = true;
            Anim.run(260, t -> {
                alpha = (float) (1 - t);
                slide = (int) Math.round(30 * t);
                repaint();
            }, done);
        }

        @Override
        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
            g2.translate(slide, 0);
            super.paint(g2);
            g2.dispose();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            for (int i = 8; i >= 1; i--) {
                g2.setColor(Theme.alpha(Color.BLACK, (int) ((Theme.isDark() ? 22 : 9) * (1 - i / 9.0))));
                g2.fill(new RoundRectangle2D.Double(-i + 1, -i + 4, w + i * 2 - 2, h + i * 2 - 2, 16 + i * 2, 16 + i * 2));
            }
            g2.setColor(Theme.isDark() ? new Color(0x1A2440) : Color.WHITE);
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            g2.setColor(Theme.border());
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 16, 16));
            g2.setColor(kind.color);
            g2.setClip(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            g2.fill(new Rectangle(0, 0, 4, h));
            g2.dispose();
        }
    }
}
