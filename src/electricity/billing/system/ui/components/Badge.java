package electricity.billing.system.ui.components;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Complaint;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import javax.swing.JComponent;

/** A coloured status pill with a dot, e.g. "● Paid". */
public class Badge extends JComponent {

    /** A badge's text and colour, for table cells. Sorts by its text. */
    public record Label(String text, Color color) implements Comparable<Label> {
        public static Label of(Bill bill, LocalDate today) {
            if (bill.isOverdue(today)) {
                return new Label("Overdue", Theme.DANGER);
            }
            return of(bill.status());
        }

        public static Label of(BillStatus status) {
            return switch (status) {
                case PAID -> new Label("Paid", Theme.SUCCESS);
                case PENDING -> new Label("Pending review", Theme.WARNING);
                case NOT_PAID -> new Label("Unpaid", Theme.AMBER);
            };
        }

        public static Label of(Complaint.Status status) {
            return switch (status) {
                case OPEN -> new Label("Open", Theme.DANGER);
                case IN_PROGRESS -> new Label("In progress", Theme.WARNING);
                case RESOLVED -> new Label("Resolved", Theme.SUCCESS);
            };
        }

        @Override
        public int compareTo(Label other) {
            return text.compareTo(other.text);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private String text;
    private Color color;
    private boolean onColor;

    public Badge(String text, Color color) {
        this.text = text;
        this.color = color;
        setFont(Theme.semibold(12));
    }

    /** White pill, for use on gradient or coloured backgrounds. */
    public Badge onColor() {
        onColor = true;
        return this;
    }

    public static Badge of(Bill bill, LocalDate today) {
        Badge badge = new Badge("", Theme.SUCCESS);
        badge.set(bill, today);
        return badge;
    }

    public static Badge of(BillStatus status) {
        Badge badge = new Badge("", Theme.SUCCESS);
        badge.set(status);
        return badge;
    }

    public void set(BillStatus status) {
        set(Label.of(status));
    }

    /** Shows "Overdue" for an unpaid bill past its due date. */
    public void set(Bill bill, LocalDate today) {
        set(Label.of(bill, today));
    }

    public void set(Label label) {
        set(label.text(), label.color());
    }

    public void set(String newText, Color newColor) {
        text = newText;
        color = newColor;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 30, 26);
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
        Dimension d = getPreferredSize();
        int y = (getHeight() - d.height) / 2;
        g2.setColor(onColor ? new Color(255, 255, 255, 235) : Theme.alpha(color, Theme.isDark() ? 48 : 30));
        g2.fill(new RoundRectangle2D.Double(0, y, d.width, d.height, d.height, d.height));
        Color ink = onColor || !Theme.isDark() ? Theme.mix(color, Color.BLACK, 0.18) : Theme.mix(color, Color.WHITE, 0.25);
        g2.setColor(ink);
        g2.fill(new Ellipse2D.Double(10, y + d.height / 2.0 - 3, 6, 6));
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, 21, y + (d.height - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
