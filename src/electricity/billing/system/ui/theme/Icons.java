package electricity.billing.system.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.Icon;

/**
 * Line icons drawn with Java2D on a 24x24 grid (stroke 2, round caps), so they
 * stay sharp at any size and take their colour from the theme at paint time.
 */
public final class Icons {

    public enum Glyph {
        BOLT, DASHBOARD, USERS, USER, USER_PLUS, RECEIPT, CALCULATOR, CREDIT_CARD, FILE_TEXT,
        SHIELD, LOGOUT, SUN, MOON, SEARCH, PRINTER, CHECK, CHECK_CIRCLE, PEN, GAUGE, CLOCK,
        TRENDING_UP, ARROW_RIGHT, ARROW_LEFT, MAIL, LOCK, HASH, INFO, ALERT, PLUS, X, MAP_PIN,
        PHONE, HOME, WALLET, COPY, REFRESH, SOLAR, CHIP
    }

    private Icons() {
    }

    public static Icon of(Glyph glyph, int size, Color color) {
        return new VectorIcon(glyph, size, () -> color, 2f);
    }

    public static Icon of(Glyph glyph, int size, Supplier<Color> color) {
        return new VectorIcon(glyph, size, color, 2f);
    }

    /** Draws a glyph directly, e.g. inside a custom-painted component. */
    public static void paint(Graphics2D g, Glyph glyph, double x, double y, double size, Color color, float stroke) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.scale(size / 24.0, size / 24.0);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Shapes s = shapes(glyph);
            for (Shape shape : s.stroked) {
                g2.draw(shape);
            }
            for (Shape shape : s.filled) {
                g2.fill(shape);
            }
        } finally {
            g2.dispose();
        }
    }

    /** The app's logo: a white bolt on a rounded brand-gradient tile. */
    public static Image appImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(Theme.brand(0, 0, size, size));
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.45, size * 0.45));
        Graphics2D bolt = (Graphics2D) g.create();
        bolt.translate(size * 0.2, size * 0.2);
        bolt.scale(size * 0.6 / 24.0, size * 0.6 / 24.0);
        bolt.setColor(Color.WHITE);
        bolt.fill(shapes(Glyph.BOLT).stroked.get(0));
        bolt.dispose();
        g.dispose();
        return image;
    }

    public static List<Image> appImages() {
        return List.of(appImage(16), appImage(24), appImage(32), appImage(48), appImage(64), appImage(128));
    }

    private static final class VectorIcon implements Icon {
        private final Glyph glyph;
        private final int size;
        private final Supplier<Color> color;
        private final float stroke;

        VectorIcon(Glyph glyph, int size, Supplier<Color> color, float stroke) {
            this.glyph = glyph;
            this.size = size;
            this.color = color;
            this.stroke = stroke;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color tint = color.get();
            if (c != null && !c.isEnabled()) {
                tint = Theme.alpha(tint, 110);
            }
            paint((Graphics2D) g, glyph, x, y, size, tint, stroke);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static final class Shapes {
        final List<Shape> stroked = new ArrayList<>();
        final List<Shape> filled = new ArrayList<>();

        Shapes s(Shape... shapes) {
            stroked.addAll(List.of(shapes));
            return this;
        }

        Shapes f(Shape... shapes) {
            filled.addAll(List.of(shapes));
            return this;
        }
    }

    private static Shapes shapes(Glyph glyph) {
        Shapes s = new Shapes();
        switch (glyph) {
            case BOLT -> s.s(closed(13, 2, 3, 14, 12, 14, 11, 22, 21, 10, 12, 10));
            case DASHBOARD -> s.s(rr(3, 3, 7, 9, 2), rr(14, 3, 7, 5, 2), rr(14, 12, 7, 9, 2), rr(3, 16, 7, 5, 2));
            case USERS -> s.s(circle(9, 7, 4), arc(2, 14, 14, 14, 0, 180),
                    arc(13, 3, 8, 8, -70, 160), arc(12, 14, 10, 14, 0, 75));
            case USER -> s.s(circle(12, 8, 4), arc(5, 14, 14, 14, 0, 180));
            case USER_PLUS -> s.s(circle(9, 8, 4), arc(2, 15, 14, 14, 0, 180), line(19, 6, 19, 12), line(16, 9, 22, 9));
            case RECEIPT -> s.s(closed(4, 2, 4, 22, 6, 21, 8, 22, 10, 21, 12, 22, 14, 21, 16, 22, 18, 21, 20, 22,
                    20, 2, 18, 3, 16, 2, 14, 3, 12, 2, 10, 3, 8, 2, 6, 3),
                    line(8, 8, 16, 8), line(8, 12, 16, 12), line(8, 16, 12, 16));
            case CALCULATOR -> {
                s.s(rr(4, 2, 16, 20, 2), line(8, 6.5, 16, 6.5));
                for (double y : new double[] {11, 14.5, 18}) {
                    for (double x : new double[] {8, 12, 16}) {
                        s.f(circle(x, y, 1.1));
                    }
                }
            }
            case CREDIT_CARD -> s.s(rr(2, 5, 20, 14, 2), line(2, 10, 22, 10), line(6, 15, 10, 15));
            case FILE_TEXT -> s.s(closed(14, 2, 6, 2, 4, 4, 4, 20, 6, 22, 18, 22, 20, 20, 20, 8),
                    open(14, 2, 14, 8, 20, 8), line(8, 13, 16, 13), line(8, 17, 16, 17), line(8, 9, 10, 9));
            case SHIELD -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 22);
                p.quadTo(20, 18, 20, 12);
                p.lineTo(20, 5);
                p.lineTo(12, 2);
                p.lineTo(4, 5);
                p.lineTo(4, 12);
                p.quadTo(4, 18, 12, 22);
                p.closePath();
                s.s(p, open(9, 12, 11, 14, 15, 10));
            }
            case LOGOUT -> s.s(open(9, 3, 5, 3, 3, 5, 3, 19, 5, 21, 9, 21), line(10, 12, 21, 12), open(16, 7, 21, 12, 16, 17));
            case SUN -> {
                s.s(circle(12, 12, 4));
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    s.s(line(12 + Math.cos(a) * 7.5, 12 + Math.sin(a) * 7.5, 12 + Math.cos(a) * 10, 12 + Math.sin(a) * 10));
                }
            }
            case MOON -> {
                Area moon = new Area(new Ellipse2D.Double(3, 3, 18, 18));
                moon.subtract(new Area(new Ellipse2D.Double(9, -1, 16, 16)));
                s.s(moon);
            }
            case SEARCH -> s.s(circle(11, 11, 7), line(16.5, 16.5, 21, 21));
            case PRINTER -> s.s(open(6, 9, 6, 2, 18, 2, 18, 9),
                    open(6, 18, 4, 18, 2, 16, 2, 11, 4, 9, 20, 9, 22, 11, 22, 16, 20, 18, 18, 18),
                    new Rectangle2D.Double(6, 14, 12, 8));
            case CHECK -> s.s(open(20, 6, 9, 17, 4, 12));
            case CHECK_CIRCLE -> s.s(circle(12, 12, 10), open(8, 12.5, 11, 15.5, 16.5, 9.5));
            case PEN -> s.s(closed(16, 3, 21, 8, 8.5, 20.5, 3, 21, 3.5, 15.5), line(14, 5, 19, 10));
            case GAUGE -> s.s(arc(3, 5, 18, 18, 0, 180), line(12, 14, 16.5, 9.5)).f(circle(12, 14, 1.8));
            case CLOCK -> s.s(circle(12, 12, 10), open(12, 6, 12, 12, 16, 14));
            case TRENDING_UP -> s.s(open(22, 7, 13.5, 15.5, 8.5, 10.5, 2, 17), open(16, 7, 22, 7, 22, 13));
            case ARROW_RIGHT -> s.s(line(5, 12, 19, 12), open(12, 5, 19, 12, 12, 19));
            case ARROW_LEFT -> s.s(line(19, 12, 5, 12), open(12, 19, 5, 12, 12, 5));
            case MAIL -> s.s(rr(2, 4, 20, 16, 2), open(22, 7, 12, 13, 2, 7));
            case LOCK -> s.s(rr(3, 11, 18, 11, 2), arc(7, 2, 10, 10, 0, 180), line(7, 7, 7, 11), line(17, 7, 17, 11));
            case HASH -> s.s(line(4, 9, 20, 9), line(4, 15, 20, 15), line(10, 3, 8, 21), line(16, 3, 14, 21));
            case INFO -> s.s(circle(12, 12, 10), line(12, 16, 12, 12)).f(circle(12, 8, 1.2));
            case ALERT -> s.s(circle(12, 12, 10), line(12, 8, 12, 12)).f(circle(12, 16, 1.2));
            case PLUS -> s.s(line(12, 5, 12, 19), line(5, 12, 19, 12));
            case X -> s.s(line(18, 6, 6, 18), line(6, 6, 18, 18));
            case MAP_PIN -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 22);
                p.quadTo(4, 15, 4, 10);
                p.append(new Arc2D.Double(4, 2, 16, 16, 180, -180, Arc2D.OPEN), true);
                p.quadTo(20, 15, 12, 22);
                s.s(p, circle(12, 10, 3));
            }
            case PHONE -> s.s(rr(6, 2, 12, 20, 2.5), line(11, 18, 13, 18));
            case HOME -> s.s(open(3, 10, 12, 3, 21, 10), open(5, 9, 5, 21, 19, 21, 19, 9), open(10, 21, 10, 15, 14, 15, 14, 21));
            case WALLET -> s.s(rr(3, 6, 18, 14, 2.5), open(16, 6, 16, 3.5, 5, 6), rr(14, 11, 7, 4, 1.5));
            case COPY -> s.s(rr(8, 8, 13, 13, 2), open(16, 8, 16, 5, 14, 3, 5, 3, 3, 5, 3, 14, 5, 16, 8, 16));
            case REFRESH -> s.s(arc(3, 3, 18, 18, 30, 280), open(21, 3, 21, 9, 15, 9));
            case SOLAR -> s.s(closed(4, 6, 20, 6, 22, 16, 2, 16), line(3, 11, 21, 11), line(12, 6, 12, 16),
                    line(12, 16, 12, 21), line(8, 21, 16, 21));
            case CHIP -> s.s(rr(6, 6, 12, 12, 2), rr(9, 9, 6, 6, 1), line(9, 2, 9, 6), line(15, 2, 15, 6),
                    line(9, 18, 9, 22), line(15, 18, 15, 22), line(2, 9, 6, 9), line(2, 15, 6, 15),
                    line(18, 9, 22, 9), line(18, 15, 22, 15));
            default -> throw new IllegalArgumentException(glyph.name());
        }
        return s;
    }

    private static Shape line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static Shape circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }

    private static Shape rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    private static Shape arc(double x, double y, double w, double h, double start, double extent) {
        return new Arc2D.Double(x, y, w, h, start, extent, Arc2D.OPEN);
    }

    private static Path2D open(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    private static Path2D closed(double... xy) {
        Path2D p = open(xy);
        p.closePath();
        return p;
    }
}
