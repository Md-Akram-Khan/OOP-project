package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Months;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Anim;
import electricity.billing.system.ui.components.Btn;
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
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Records a meter reading; a dark preview card prices the bill live as the admin types. */
public class CalculateBillPage extends Page {

    private final JComboBox<Customer> customer = Field.combo();
    private final JComboBox<String> month = Field.combo();
    private final JComboBox<Integer> year = Field.combo();
    private final Field units = Field.text("Units consumed", "e.g. 240", Glyph.GAUGE);
    private final Btn save = new Btn("Save bill", Glyph.CHECK, Btn.Style.PRIMARY).fullWidth();

    private final Txt previewName = white("Choose a customer", Theme.semibold(18), 255);
    private final Txt previewTariff = white(" ", Theme.regular(13), 170);
    private final Txt energy = white("—", Theme.semibold(14), 255);
    private final Txt energyDetail = white(" ", Theme.regular(12), 150);
    private final Txt fixed = white("—", Theme.semibold(14), 255);
    private final GradientText total = new GradientText(Fmt.money(0), Theme.semibold(40), Theme.AQUA, Theme.AMBER);
    private final Txt hint = white("Enter units to see the total.", Theme.regular(13), 160);
    private final Timer debounce;
    private double shownTotal;
    private Timer totalAnim;
    private String pendingMeter;

    public CalculateBillPage(AppContext app) {
        super(app);
        add(header("Calculate bill", "Record a meter reading. The bill is priced live as you type."), BorderLayout.NORTH);

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
        customer.addActionListener(e -> debounce.restart());
        units.textField().getDocument().addDocumentListener(new DocumentListener() {
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
        });
        save.addActionListener(e -> saveBill());

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(18,
                Ui.vbox(4, Txt.h2("Meter reading"), Txt.small("One bill per customer per month.")),
                Field.of("Customer", customer),
                Ui.columns(16, Field.of("Month", month), Field.of("Year", year)),
                units,
                save), BorderLayout.NORTH);

        Card preview = new Card(new BorderLayout()).pad(28, 28, 28, 28)
                .fill(r -> new GradientPaint(r.x, r.y, Theme.INK_TOP, r.x + r.width, r.y + r.height, new Color(0x15296A)));
        Txt caps = new Txt("BILL PREVIEW", Theme.semibold(11).deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.14)),
                () -> Theme.AQUA);
        preview.add(Ui.vbox(10,
                caps, previewName, previewTariff,
                divider(),
                line("Energy charge", energy), energyDetail,
                line("Fixed charges", fixed),
                white("Meter rent, service charge, service tax and fixed tax", Theme.regular(12), 150),
                divider(),
                white("Total payable", Theme.regular(14), 190), total, hint), BorderLayout.NORTH);

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
        Object previous = customer.getSelectedItem();
        customer.removeAllItems();
        list.forEach(customer::addItem);
        String wanted = pendingMeter != null ? pendingMeter : previous instanceof Customer c ? c.meterNo() : null;
        for (Customer c : list) {
            if (c.meterNo().equals(wanted)) {
                customer.setSelectedItem(c);
            }
        }
        if (list.isEmpty()) {
            previewName.setText("No customers yet");
            hint.setText("Add a customer first.");
        }
        refreshPreview();
    }

    private Integer parseUnits() {
        try {
            int value = Integer.parseInt(units.value());
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refreshPreview() {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null) {
            return;
        }
        previewName.setText(c.name());
        Integer u = parseUnits();
        units.setError(units.value().isEmpty() || u != null ? null : "Enter a whole number of units.");
        if (u == null) {
            hint.setText("Enter units to see the total.");
            hint.setVisible(true);
            return;
        }
        String meterNo = c.meterNo();
        Tasks.run(this, () -> app.billing.preview(meterNo, u), p -> showPreview(p, meterNo));
    }

    private void showPreview(Api.BillPreview p, String meterNo) {
        Customer c = (Customer) customer.getSelectedItem();
        if (c == null || !c.meterNo().equals(meterNo)) {
            return;
        }
        boolean industrial = p.billType() == BillType.INDUSTRIAL;
        previewTariff.setText((industrial ? "Industrial" : "Normal") + " tariff  ·  Meter #" + meterNo);
        energy.setText(Fmt.money(p.energyCharge()));
        energyDetail.setText(p.units() + " units × " + Fmt.money(p.rates().costPerUnit())
                + (industrial ? " × " + p.rates().industrialMultiplier().stripTrailingZeros().toPlainString() : ""));
        fixed.setText(Fmt.money(p.rates().fixedCharges()));
        hint.setVisible(false);
        animateTotal(p.total());
    }

    private void animateTotal(BigDecimal target) {
        Anim.stop(totalAnim);
        double from = shownTotal;
        double to = target.doubleValue();
        totalAnim = Anim.run(500, t -> {
            shownTotal = Anim.lerp(from, to, t);
            total.setText(Fmt.money(t >= 1 ? to : shownTotal));
        });
    }

    private void saveBill() {
        Customer c = (Customer) customer.getSelectedItem();
        Integer u = parseUnits();
        if (c == null) {
            Toast.error(this, "Choose a customer first.");
            return;
        }
        if (u == null) {
            units.setError("Enter a whole number of units.");
            return;
        }
        String m = (String) month.getSelectedItem();
        int y = (Integer) year.getSelectedItem();
        save.setBusy(true, "Saving…");
        Tasks.run(() -> app.billing.createBill(c.meterNo(), m, y, u), bill -> {
            save.setBusy(false, null);
            Toast.success(this, "Saved " + c.name() + "'s " + bill.month() + " bill: " + Fmt.money(bill.total()) + ".");
            units.setValue("");
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
