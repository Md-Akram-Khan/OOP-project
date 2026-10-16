package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Months;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Anim;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.GradientText;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.math.BigDecimal;
import java.time.Year;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Records a meter reading. The admin types today's reading (the previous one
 * comes from the last bill) and a dark preview card prices the bill live with
 * the BERC slab tariff, while the bill checker flags anything that looks wrong.
 */
public class CalculateBillPage extends Page {

    private final JComboBox<Customer> customer = Field.combo();
    private final JComboBox<String> month = Field.combo();
    private final JComboBox<Integer> year = Field.combo();
    private final Field previous = Field.text("Previous reading", "From the last bill", Glyph.CLOCK);
    private final Field current = Field.text("Current reading", "Number on the meter today", Glyph.GAUGE);
    private final Txt meterInfo = Txt.small(" ");
    private final Txt previousHint = Txt.small(" ").wrap(190);
    private final Callout warnings = new Callout(Glyph.ALERT, Theme.WARNING, "Check before saving", 360);
    private final Btn save = new Btn("Save bill", Glyph.CHECK, Btn.Style.PRIMARY).fullWidth();

    private final Txt previewName = white("Choose a customer", Theme.semibold(18), 255);
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
    private final Txt hint = white("Enter the current reading to see the bill.", Theme.regular(13), 160).wrap(340);
    private final Timer debounce;
    private double shownTotal;
    private Timer totalAnim;
    private String pendingMeter;
    private Api.BillPreview lastPreview;
    private int requestId;

    public CalculateBillPage(AppContext app) {
        super(app);
        add(header("Calculate bill", "Enter the meter reading. The bill is priced live with the BERC slab tariff."), BorderLayout.NORTH);

        customer.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Customer c) {
                    setText(c.name() + "   ·   #" + c.meterNo());
                }
                return this;
            }
        });
        Months.NAMES.forEach(month::addItem);
        month.setSelectedItem(Months.current());
        int now = Year.now().getValue();
        for (int y = now; y >= now - 5; y--) {
            year.addItem(y);
        }

        debounce = new Timer(350, e -> refreshPreview());
        debounce.setRepeats(false);
        customer.addActionListener(e -> loadLastReading());
        month.addActionListener(e -> debounce.restart());
        year.addActionListener(e -> debounce.restart());
        DocumentListener typing = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        };
        previous.textField().getDocument().addDocumentListener(typing);
        current.textField().getDocument().addDocumentListener(typing);
        save.addActionListener(e -> saveBill());
        warnings.setVisible(false);

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(18,
                Ui.vbox(4, Txt.h2("Meter reading"), Txt.small("One bill per customer per month. Units = current − previous.")),
                Ui.vbox(6, Field.of("Customer", customer), meterInfo),
                Ui.columns(16, Field.of("Month", month), Field.of("Year", year)),
                Ui.columns(16, Ui.vbox(4, previous, previousHint), current),
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
        row.add(Ui.fixedWidth(preview, 420), BorderLayout.EAST);
        body(row);
    }

    @Override
    public void onShow(Object param) {
        pendingMeter = param instanceof String meter ? meter : null;
        Tasks.run(this, app.customers::findAll, this::fillCustomers);
    }

    private void fillCustomers(List<Customer> list) {
        Object previousChoice = customer.getSelectedItem();
        customer.removeAllItems();
        list.forEach(customer::addItem);
        String wanted = pendingMeter != null ? pendingMeter : previousChoice instanceof Customer c ? c.meterNo() : null;
        for (Customer c : list) {
            if (c.meterNo().equals(wanted)) {
                customer.setSelectedItem(c);
            }
        }
        if (list.isEmpty()) {
            previewName.setText("No customers yet");
            hint.setText("Add a customer first.");
        }
        loadLastReading();
    }

    /** Fills in the previous reading from the customer's last bill. */
    private void loadLastReading() {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null) {
            return;
        }
        previewName.setText(c.name());
        String meterNo = c.meterNo();
        Tasks.run(() -> app.billing.lastReading(meterNo), last -> {
            Customer still = (Customer) customer.getSelectedItem();
            if (still == null || !still.meterNo().equals(meterNo)) {
                return;
            }
            meterInfo.setText(last.billType().displayName() + "  ·  " + Fmt.kw(last.loadKw()) + " sanctioned load  ·  " + last.planName());
            if (last.reading() != null) {
                previous.setValue(String.valueOf(last.reading()));
                previousHint.setText("From the " + last.period() + " bill.");
            } else {
                previous.setValue("");
                previousHint.setText("No reading on file: enter it from the meter or last paper bill.");
            }
            refreshPreview();
        }, error -> Toast.error(this, Tasks.message(error)));
    }

    /** A whole, non-negative number, or null if the text isn't one. */
    private static Integer parse(Field field) {
        try {
            int value = Integer.parseInt(field.value());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refreshPreview() {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null || month.getSelectedItem() == null || year.getSelectedItem() == null) {
            return;
        }
        Integer prev = parse(previous);
        Integer curr = parse(current);
        previous.setError(previous.value().isEmpty() || prev != null ? null : "Enter a whole number.");
        current.setError(current.value().isEmpty() || curr != null ? null : "Enter a whole number.");
        if (curr == null) {
            showHint("Enter the current reading to see the bill.");
            return;
        }
        String meterNo = c.meterNo();
        String m = (String) month.getSelectedItem();
        int y = (Integer) year.getSelectedItem();
        int id = ++requestId;
        Tasks.run(() -> app.billing.preview(meterNo, m, y, prev, curr), p -> {
            if (id == requestId) {
                showPreview(p);
            }
        }, error -> {
            if (id == requestId) {
                showHint(Tasks.message(error));
            }
        });
    }

    private void showHint(String text) {
        lastPreview = null;
        hint.setText(text);
        hint.setVisible(true);
        previewUnits.setText(" ");
        warnings.setVisible(false);
    }

    private void showPreview(Api.BillPreview p) {
        lastPreview = p;
        BillBreakdown b = p.breakdown();
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
        Customer c = (Customer) customer.getSelectedItem();
        Integer prev = parse(previous);
        Integer curr = parse(current);
        if (c == null) {
            Toast.error(this, "Choose a customer first.");
            return;
        }
        if (curr == null) {
            current.setError("Enter the reading shown on the meter.");
            return;
        }
        if (!previous.value().isEmpty() && prev == null) {
            previous.setError("Enter a whole number.");
            return;
        }
        boolean acknowledged = false;
        if (lastPreview != null && !lastPreview.warnings().isEmpty()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "The bill checker found:\n\n• " + String.join("\n• ", lastPreview.warnings()) + "\n\nSave this bill anyway?",
                    "Check before saving", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            acknowledged = true;
        }
        String m = (String) month.getSelectedItem();
        int y = (Integer) year.getSelectedItem();
        boolean ack = acknowledged;
        save.setBusy(true, "Saving…");
        Tasks.run(() -> app.billing.createBill(c.meterNo(), m, y, prev, curr, ack), bill -> {
            save.setBusy(false, null);
            Toast.success(this, "Saved " + c.name() + "'s " + bill.month() + " bill: " + Fmt.taka(bill.total())
                    + ", due " + Fmt.date(bill.dueDate()) + ".");
            current.setValue("");
            loadLastReading();
        }, error -> {
            save.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
            refreshPreview();
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
