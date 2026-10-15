package electricity.billing.system.ui.components;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatement;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.TaxRates;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextAttribute;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.math.BigDecimal;
import java.util.Map;
import javax.swing.JComponent;

/** The bill as a styled invoice document, painted directly so it prints exactly as it looks. */
public class InvoiceView extends JComponent {

    private static final int SHEET_WIDTH = 760;
    private static final int SHEET_HEIGHT = 820;
    private static final int PAD = 40;

    private record Palette(Color sheet, Color text, Color muted, Color border, Color alt) {
        static Palette screen() {
            return new Palette(Theme.surface(), Theme.text(), Theme.muted(), Theme.border(), Theme.surfaceAlt());
        }

        static Palette print() {
            return new Palette(Color.WHITE, new Color(0x101828), new Color(0x667085), new Color(0xE4E8F1), new Color(0xF6F8FC));
        }
    }

    private BillStatement statement;
    private String month;
    private int year;

    public void show(BillStatement newStatement, String newMonth, int newYear) {
        statement = newStatement;
        month = newMonth;
        year = newYear;
        revalidate();
        repaint();
    }

    public boolean hasBill() {
        return statement != null && statement.bill() != null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SHEET_WIDTH + Card.SHADOW * 2, SHEET_HEIGHT + Card.SHADOW * 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (statement == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        int x = Math.max(Card.SHADOW, (getWidth() - SHEET_WIDTH) / 2);
        Rectangle sheet = new Rectangle(x, Card.SHADOW, SHEET_WIDTH, SHEET_HEIGHT);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Card.paintShadow(g2, sheet, 0);
        g2.translate(sheet.x, sheet.y);
        paintSheet(g2, Palette.screen());
        g2.dispose();
    }

    /** Sends the invoice to a printer, scaled to fit the page. */
    public void print() throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Electricity bill " + month + " " + year);
        job.setPrintable((graphics, format, page) -> {
            if (page > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.translate(format.getImageableX(), format.getImageableY());
            double scale = Math.min(format.getImageableWidth() / SHEET_WIDTH, format.getImageableHeight() / SHEET_HEIGHT);
            g2.scale(scale, scale);
            paintSheet(g2, Palette.print());
            g2.dispose();
            return Printable.PAGE_EXISTS;
        });
        if (job.printDialog()) {
            job.print();
        }
    }

    private void paintSheet(Graphics2D g, Palette p) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = SHEET_WIDTH;
        RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, SHEET_HEIGHT, 20, 20);
        g.setColor(p.sheet());
        g.fill(shape);
        g.setColor(p.border());
        g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, SHEET_HEIGHT - 1, 20, 20));

        Customer c = statement.customer();
        Meter m = statement.meter();
        TaxRates r = statement.rates();
        Bill b = statement.bill();

        // Header band
        java.awt.Shape oldClip = g.getClip();
        g.clip(shape);
        g.setPaint(new GradientPaint(0, 0, Theme.BLUE, w, 130, Theme.AQUA));
        g.fillRect(0, 0, w, 130);
        g.setClip(oldClip);
        g.setColor(new Color(255, 255, 255, 40));
        g.fill(new RoundRectangle2D.Double(PAD, 38, 54, 54, 18, 18));
        Icons.paint(g, Glyph.BOLT, PAD + 13, 51, 28, Color.WHITE, 2.2f);
        text(g, "Electricity Bill", Theme.semibold(24), Color.WHITE, PAD + 72, 64);
        String ref = "Invoice EB-" + c.meterNo() + "-" + year + String.format("%02d", Fmt.monthIndex(month) + 1);
        text(g, ref, Theme.regular(13), new Color(255, 255, 255, 210), PAD + 72, 88);
        textRight(g, "BILLING PERIOD", caps(), new Color(255, 255, 255, 190), w - PAD, 60);
        textRight(g, Fmt.period(month, year), Theme.semibold(22), Color.WHITE, w - PAD, 90);

        // Parties
        int y = 172;
        text(g, "BILLED TO", caps(), p.muted(), PAD, y);
        text(g, c.name(), Theme.semibold(17), p.text(), PAD, y + 28);
        text(g, c.address(), Theme.regular(14), p.muted(), PAD, y + 52);
        text(g, c.city() + ", " + c.state(), Theme.regular(14), p.muted(), PAD, y + 74);
        text(g, c.email() + "  ·  " + c.phone(), Theme.regular(14), p.muted(), PAD, y + 96);

        int col = w / 2 + 30;
        text(g, "METER", caps(), p.muted(), col, y);
        text(g, "No. " + c.meterNo(), Theme.semibold(17), p.text(), col, y + 28);
        BillType type = m == null ? BillType.NORMAL : m.billType();
        String tariff = type == BillType.INDUSTRIAL
                ? "Industrial tariff (× " + r.industrialMultiplier().stripTrailingZeros().toPlainString() + ")" : "Normal tariff";
        text(g, m == null ? "Meter not set up" : m.meterType(), Theme.regular(14), p.muted(), col, y + 52);
        text(g, tariff, Theme.regular(14), p.muted(), col, y + 74);
        text(g, "Billing days: " + (m == null ? 30 : m.days()), Theme.regular(14), p.muted(), col, y + 96);

        y = 318;
        if (b == null) {
            g.setColor(p.alt());
            g.fill(new RoundRectangle2D.Double(PAD, y, w - PAD * 2, 300, 16, 16));
            Icons.paint(g, Glyph.FILE_TEXT, w / 2.0 - 22, y + 90, 44, p.muted(), 1.8f);
            centered(g, "No bill for " + Fmt.period(month, year) + " yet", Theme.semibold(17), p.text(), w / 2, y + 170);
            centered(g, "Bills appear here once the admin records your meter reading.", Theme.regular(14), p.muted(), w / 2, y + 196);
            footer(g, p);
            return;
        }

        // Line items
        g.setColor(p.alt());
        g.fill(new RoundRectangle2D.Double(PAD, y, w - PAD * 2, 40, 12, 12));
        text(g, "DESCRIPTION", caps(), p.muted(), PAD + 18, y + 25);
        textRight(g, "AMOUNT", caps(), p.muted(), w - PAD - 18, y + 25);
        y += 40;
        BigDecimal energy = b.total().subtract(r.fixedCharges());
        String detail = b.units() + " units × " + Fmt.money(r.costPerUnit())
                + (type == BillType.INDUSTRIAL ? " × " + r.industrialMultiplier().stripTrailingZeros().toPlainString() : "");
        y = item(g, p, y, "Energy charge", detail, energy);
        y = item(g, p, y, "Meter rent", null, r.meterRent());
        y = item(g, p, y, "Service charge", null, r.serviceCharge());
        y = item(g, p, y, "Service tax", null, r.serviceTax());
        y = item(g, p, y, "Fixed tax", null, r.fixedTax());

        // Total
        y += 28;
        textRight(g, "Total payable", Theme.regular(14), p.muted(), w - PAD, y);
        Font big = Theme.semibold(34);
        g.setFont(big);
        FontMetrics fm = g.getFontMetrics();
        String total = Fmt.money(b.total());
        int tw = fm.stringWidth(total);
        g.setPaint(new GradientPaint(w - PAD - tw, 0, Theme.BLUE, w - PAD, 0, Theme.AQUA));
        g.drawString(total, w - PAD - tw, y + 44);

        // Status
        Color statusColor = switch (b.status()) {
            case PAID -> Theme.SUCCESS;
            case PENDING -> Theme.WARNING;
            default -> Theme.DANGER;
        };
        String statusText = switch (b.status()) {
            case PAID -> "Paid";
            case PENDING -> "Pending review";
            default -> "Unpaid";
        };
        g.setFont(Theme.semibold(13));
        fm = g.getFontMetrics();
        int pw = fm.stringWidth(statusText) + 34;
        g.setColor(Theme.alpha(statusColor, 34));
        g.fill(new RoundRectangle2D.Double(PAD, y + 14, pw, 30, 30, 30));
        g.setColor(Theme.mix(statusColor, Color.BLACK, 0.15));
        g.fill(new java.awt.geom.Ellipse2D.Double(PAD + 12, y + 26, 7, 7));
        g.drawString(statusText, PAD + 25, y + 34);
        String note = b.status() == BillStatus.PAID ? "Thank you — this bill is fully paid."
                : b.status() == BillStatus.PENDING ? "Payment received, awaiting confirmation."
                : "Pay online from the Pay bill page.";
        text(g, note, Theme.regular(13), p.muted(), PAD, y + 66);

        footer(g, p);
    }

    private int item(Graphics2D g, Palette p, int y, String label, String detail, BigDecimal amount) {
        int rowH = detail == null ? 46 : 60;
        text(g, label, Theme.semibold(14), p.text(), PAD + 18, y + (detail == null ? 29 : 26));
        if (detail != null) {
            text(g, detail, Theme.regular(12), p.muted(), PAD + 18, y + 46);
        }
        textRight(g, Fmt.money(amount), Theme.semibold(14), p.text(), SHEET_WIDTH - PAD - 18, y + (detail == null ? 29 : 26));
        g.setColor(p.border());
        g.draw(new Line2D.Double(PAD, y + rowH, SHEET_WIDTH - PAD, y + rowH));
        return y + rowH;
    }

    private void footer(Graphics2D g, Palette p) {
        g.setColor(p.border());
        g.draw(new Line2D.Double(PAD, SHEET_HEIGHT - 58, SHEET_WIDTH - PAD, SHEET_HEIGHT - 58));
        text(g, "Generated " + Fmt.today(), Theme.regular(12), p.muted(), PAD, SHEET_HEIGHT - 30);
        textRight(g, "Electricity Billing System", Theme.semibold(12), p.muted(), SHEET_WIDTH - PAD, SHEET_HEIGHT - 30);
    }

    private static Font caps() {
        return Theme.semibold(11).deriveFont(Map.of(TextAttribute.TRACKING, 0.12));
    }

    private static void text(Graphics2D g, String s, Font f, Color c, int x, int baseline) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s == null ? "" : s, x, baseline);
    }

    private static void textRight(Graphics2D g, String s, Font f, Color c, int right, int baseline) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, right - g.getFontMetrics().stringWidth(s), baseline);
    }

    private static void centered(Graphics2D g, String s, Font f, Color c, int cx, int baseline) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, baseline);
    }
}
