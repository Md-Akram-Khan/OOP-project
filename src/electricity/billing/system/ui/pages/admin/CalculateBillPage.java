package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.Months;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Anim;
import electricity.billing.system.ui.components.Avatar;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.GradientText;
import electricity.billing.system.ui.components.Photos;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.io.File;
import java.math.BigDecimal;
import java.time.Year;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Makes a bill from nothing but a photo of the meter. The server works out
 * which customer's meter it is from the number printed on it, reads the
 * current reading off the display, and fills in the previous reading from the
 * last bill. The admin only types the previous reading for a meter's very
 * first bill, when there's none on file.
 */
public class CalculateBillPage extends Page {

    private static final int THUMB_W = 250;
    private static final int THUMB_H = 170;

    private final JComboBox<String> month = Field.combo();
    private final JComboBox<Integer> year = Field.combo();

    // Photo
    private final JLabel thumb = new JLabel();
    private final Btn choosePhoto = new Btn("Choose photo", Glyph.FILE_TEXT, Btn.Style.PRIMARY);
    private final Txt photoStatus = Txt.small("Take it straight on, showing the display and the meter number.").wrap(300);
    private final Txt readLine = Txt.h3(" ").wrap(210);
    private Photos.Upload photo;

    // The meter found in the photo
    private final Card identified = new Card(new BorderLayout(14, 0)).pad(16, 18, 16, 18);
    private final JPanel avatarSlot = Ui.clear(new BorderLayout());
    private final Txt meterTitle = Txt.h3(" ");
    private final Txt meterDetail = Txt.small(" ").wrap(330);
    private final Txt previousLine = Txt.body(" ").wrap(330);
    private final Field previous = Field.text("Previous reading (kWh)", "From the meter's last paper bill", Glyph.CLOCK);
    private final Btn applyPrevious = new Btn("Calculate", Glyph.CALCULATOR, Btn.Style.SECONDARY);
    private final JPanel previousEntry;

    private final Callout problem = new Callout(Glyph.ALERT, Theme.DANGER, "This photo can't be used", 430);
    private final Callout warnings = new Callout(Glyph.ALERT, Theme.WARNING, "Check before saving", 430);
    private final Btn save = new Btn("Save bill", Glyph.CHECK, Btn.Style.PRIMARY).fullWidth();

    private final Txt previewName = white("Upload a meter photo", Theme.semibold(18), 255);
    private final Txt previewTariff = white(" ", Theme.regular(13), 170);
    private final Txt previewUnits = white(" ", Theme.regular(13), 200);
    private final JPanel slabRows = Ui.vbox(8);
    private final Txt demand = white("—", Theme.semibold(14), 255);
    private final Txt demandDetail = white(" ", Theme.regular(12), 150);
    private final Txt vat = white("—", Theme.semibold(14), 255);
    private final Txt rent = white("—", Theme.semibold(14), 255);
    private final Txt dueLabel = white("Total payable", Theme.regular(14), 190);
    private final GradientText total = new GradientText(Fmt.money(0), Theme.semibold(40), Theme.AQUA, Theme.AMBER);
    private final Txt afterDue = white(" ", Theme.semibold(13), 220);
    private final Txt hint = white("The customer is found from the meter number in the photo.", Theme.regular(13), 160).wrap(340);
    private double shownTotal;
    private javax.swing.Timer totalAnim;
    private Api.BillPreview lastPreview;
    private Integer enteredPrevious;
    private int requestId;

    public CalculateBillPage(AppContext app) {
        super(app);
        add(header("Calculate bill", "Upload a photo of the meter. The customer and the reading are taken from the photo."),
                BorderLayout.NORTH);

        Months.NAMES.forEach(month::addItem);
        month.setSelectedItem(Months.current());
        int now = Year.now().getValue();
        for (int y = now; y >= now - 5; y--) {
            year.addItem(y);
        }
        month.addActionListener(e -> refreshPreview());
        year.addActionListener(e -> refreshPreview());
        choosePhoto.addActionListener(e -> choosePhoto());
        applyPrevious.addActionListener(e -> applyPrevious());
        previous.textField().addActionListener(e -> applyPrevious());
        save.addActionListener(e -> saveBill());
        save.setEnabled(false);

        thumb.setHorizontalAlignment(SwingConstants.CENTER);
        thumb.setPreferredSize(new Dimension(THUMB_W, THUMB_H));
        thumb.setBorder(new LineBorder(Theme.border(), 1, true));
        JPanel photoRow = Ui.clear(new BorderLayout(18, 0));
        photoRow.add(thumb, BorderLayout.WEST);
        photoRow.add(Ui.vbox(10, Txt.caps("Meter photo"), photoStatus, Ui.hbox(0, choosePhoto), readLine), BorderLayout.CENTER);

        JPanel prevRow = Ui.clear(new BorderLayout(10, 0));
        prevRow.add(previous, BorderLayout.CENTER);
        prevRow.add(Ui.top(Ui.pad(Ui.hbox(0, applyPrevious), 26, 0, 0, 0)), BorderLayout.EAST);
        previousEntry = Ui.vbox(6, prevRow, Txt.small("This meter has no reading on file yet, so this is its first bill. "
                + "Enter the reading from its last paper bill; after this it's filled in automatically.").wrap(420));
        identified.add(avatarSlot, BorderLayout.WEST);
        identified.add(Ui.vbox(4, Txt.caps("Meter found in the photo"), meterTitle, meterDetail, previousLine, previousEntry),
                BorderLayout.CENTER);

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(18,
                Ui.vbox(4, Txt.h2("Meter reading"),
                        Txt.small("One bill per customer per month. Units = reading in the photo − previous reading.")),
                Ui.columns(16, Field.of("Month", month), Field.of("Year", year)),
                photoRow,
                identified,
                problem,
                warnings,
                save), BorderLayout.NORTH);

        Card preview = new Card(new BorderLayout()).pad(28, 28, 28, 28)
                .fill(r -> new GradientPaint(r.x, r.y, Theme.INK_TOP, r.x + r.width, r.y + r.height, new Color(0x15296A)));
        Txt caps = new Txt("BILL PREVIEW", Theme.semibold(11).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.14)),
                () -> Theme.AQUA);
        preview.add(Ui.vbox(10,
                caps, previewName, previewTariff, previewUnits,
                divider(),
                slabRows,
                line("Demand charge", demand), demandDetail,
                line("VAT", vat),
                line("Meter rent", rent),
                divider(),
                dueLabel, total, afterDue, hint), BorderLayout.NORTH);

        JPanel row = Ui.clear(new BorderLayout(16, 0));
        row.add(form, BorderLayout.CENTER);
        row.add(Ui.top(Ui.fixedWidth(preview, 400)), BorderLayout.EAST);
        body(row);
        clearPhoto();
    }

    @Override
    public void onShow(Object param) {
        if (photo == null) {
            clearPhoto();
        }
    }

    // ---- Photo --------------------------------------------------------------

    private void choosePhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a photo of the meter");
        chooser.setFileFilter(new FileNameExtensionFilter("Photos (JPEG, PNG)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        choosePhoto.setBusy(true, "Opening…");
        Tasks.run(() -> Photos.load(file), upload -> {
            choosePhoto.setBusy(false, null);
            photo = upload;
            enteredPrevious = null;
            previous.setValue("");
            thumb.setIcon(Photos.icon(upload.image(), THUMB_W - 4, THUMB_H - 4));
            choosePhoto.setText("Change photo");
            refreshPreview();
        }, error -> {
            choosePhoto.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }

    private void clearPhoto() {
        photo = null;
        lastPreview = null;
        enteredPrevious = null;
        previous.setValue("");
        thumb.setIcon(Icons.of(Glyph.GAUGE, 42, Theme::muted));
        choosePhoto.setText("Choose photo");
        photoStatus.setText("Take it straight on, showing the display and the meter number.");
        readLine.setText(" ");
        identified.setVisible(false);
        problem.setVisible(false);
        warnings.setVisible(false);
        save.setEnabled(false);
        previewName.setText("Upload a meter photo");
        showHint("The customer is found from the meter number in the photo.");
    }

    private void applyPrevious() {
        try {
            int value = Integer.parseInt(previous.value());
            if (value < 0) {
                throw new NumberFormatException();
            }
            previous.setError(null);
            enteredPrevious = value;
            refreshPreview();
        } catch (NumberFormatException e) {
            previous.setError("Enter the previous reading as a whole number.");
        }
    }

    /** Sends the photo to the server, which identifies the meter, reads it and prices the bill. */
    private void refreshPreview() {
        if (photo == null || month.getSelectedItem() == null || year.getSelectedItem() == null) {
            return;
        }
        String m = (String) month.getSelectedItem();
        int y = (Integer) year.getSelectedItem();
        byte[] bytes = photo.bytes();
        Integer prev = enteredPrevious;
        int id = ++requestId;
        photoStatus.setText("Reading the photo and finding the meter…");
        save.setEnabled(false);
        Tasks.run(() -> app.billing.preview(m, y, prev, bytes), p -> {
            if (id == requestId) {
                photoStatus.setText("Read by the server from the photo.");
                showPreview(p);
            }
        }, error -> {
            if (id == requestId) {
                photoStatus.setText("The photo couldn't be used.");
                readLine.setText(" ");
                identified.setVisible(false);
                problem.lines(Tasks.message(error));
                problem.setVisible(true);
                previewName.setText("No bill yet");
                showHint("Retake the photo: hold the phone straight, fill the frame with the meter's face, avoid glare.");
                revalidate();
            }
        });
    }

    private void showHint(String text) {
        lastPreview = null;
        hint.setText(text);
        hint.setVisible(true);
        previewTariff.setText(" ");
        previewUnits.setText(" ");
        slabRows.removeAll();
        demand.setText("—");
        demandDetail.setText(" ");
        vat.setText("—");
        rent.setText("—");
        dueLabel.setText("Total payable");
        afterDue.setText(" ");
        total.setText(Fmt.money(0));
        shownTotal = 0;
        warnings.setVisible(false);
        save.setEnabled(false);
    }

    private void showPreview(Api.BillPreview p) {
        problem.setVisible(false);
        readLine.setText("Reads " + p.photo().displayText() + " kWh → " + Fmt.number(p.currentReading()));

        // The meter the photo shows
        avatarSlot.removeAll();
        avatarSlot.add(Ui.top(new Avatar(p.customerName(), 44)), BorderLayout.NORTH);
        meterTitle.setText(p.customerName() + "  ·  Meter " + p.meterNo());
        meterDetail.setText("✓ Meter number found in the photo  ·  " + p.billType().displayName() + "  ·  " + Fmt.kw(p.loadKw()));
        meterDetail.color(() -> Theme.SUCCESS);
        identified.setVisible(true);

        if (p.needsPreviousReading()) {
            previousLine.setVisible(false);
            previousEntry.setVisible(true);
            showHint("This is the meter's first bill. Enter its previous reading to see the bill.");
            previewName.setText(p.customerName());
            previewTariff.setText(p.billType().displayName() + "  ·  " + p.planName());
            revalidate();
            repaint();
            return;
        }
        boolean onFile = p.previousPeriod() != null;
        previousEntry.setVisible(!onFile);
        previousLine.setVisible(true);
        previousLine.setText(onFile
                ? "Previous reading: " + Fmt.number(p.previousReading()) + " kWh, from the " + p.previousPeriod() + " bill"
                : "Previous reading: " + Fmt.number(p.previousReading()) + " kWh, entered for this first bill");

        lastPreview = p;
        BillBreakdown b = p.breakdown();
        previewName.setText(p.customerName());
        previewTariff.setText(p.billType().displayName() + "  ·  " + p.planName());
        previewUnits.setText(Fmt.number(p.previousReading()) + " → " + Fmt.number(p.currentReading()) + "  =  "
                + Fmt.number(b.units()) + " units" + (b.lifeline() ? "  ·  lifeline rate" : "")
                + "  ·  " + p.slab().slabLabel() + " slab");
        slabRows.removeAll();
        if (b.lines().isEmpty()) {
            slabRows.add(line("Energy", white(Fmt.money(BigDecimal.ZERO), Theme.semibold(14), 255)));
        }
        for (BillBreakdown.Line l : b.lines()) {
            slabRows.add(line("Energy · " + l.label(), white(Fmt.money(l.amount()), Theme.semibold(14), 255)));
            slabRows.add(Ui.left(white(Fmt.number(l.units()) + " units × " + Fmt.rate(l.rate()), Theme.regular(12), 150)));
        }
        slabRows.revalidate();
        demand.setText(Fmt.money(b.demand()));
        demandDetail.setText(Fmt.kw(b.loadKw()) + " × " + Fmt.rate(b.demandRate()) + " per kW");
        vat.setText(Fmt.money(b.vat()));
        rent.setText(Fmt.money(b.meterRent()));
        dueLabel.setText("Pay by " + Fmt.date(p.dueDate()));
        afterDue.setText("After the due date: " + Fmt.taka(b.payableAfterDue()) + "  (+" + Fmt.percent(b.lateFeePercent()) + " late fee)");
        hint.setVisible(false);
        warnings.setVisible(!p.warnings().isEmpty());
        warnings.title(p.warnings().size() == 1 ? "The bill checker found 1 thing to check"
                : "The bill checker found " + p.warnings().size() + " things to check").lines(p.warnings());
        save.setEnabled(true);
        animateTotal(b.payable());
        revalidate();
        repaint();
    }

    private void animateTotal(BigDecimal target) {
        Anim.stop(totalAnim);
        double from = shownTotal;
        double to = target.doubleValue();
        totalAnim = Anim.run(500, t -> {
            shownTotal = Anim.lerp(from, to, t);
            total.setText(t >= 1 ? Fmt.taka(target) : Fmt.money(shownTotal));
        });
    }

    private void saveBill() {
        Api.BillPreview p = lastPreview;
        if (photo == null || p == null) {
            Toast.error(this, "Upload a photo of the meter first.");
            return;
        }
        boolean acknowledged = false;
        if (!p.warnings().isEmpty()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "The bill checker found:\n\n• " + String.join("\n• ", p.warnings()) + "\n\nSave this bill anyway?",
                    "Check before saving", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            acknowledged = true;
        }
        String m = (String) month.getSelectedItem();
        int y = (Integer) year.getSelectedItem();
        Integer prev = p.previousPeriod() == null ? enteredPrevious : null;
        byte[] bytes = photo.bytes();
        boolean ack = acknowledged;
        save.setBusy(true, "Saving…");
        Tasks.run(() -> app.billing.createBill(m, y, prev, bytes, p.meterNo(), ack), bill -> {
            save.setBusy(false, null);
            Toast.success(this, "Saved " + p.customerName() + "'s " + bill.month() + " bill: " + Fmt.taka(bill.total())
                    + ", due " + Fmt.date(bill.dueDate()) + ".");
            clearPhoto();
        }, error -> {
            save.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }

    private static Txt white(String text, java.awt.Font font, int alpha) {
        return new Txt(text, font, () -> new Color(255, 255, 255, alpha));
    }

    private static JPanel line(String label, Txt value) {
        JPanel row = Ui.clear(new BorderLayout());
        row.add(white(label, Theme.regular(14), 200), BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private static JPanel divider() {
        JPanel line = new JPanel() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(new Color(255, 255, 255, 26));
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        line.setOpaque(false);
        line.setPreferredSize(new Dimension(10, 14));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        return line;
    }
}
