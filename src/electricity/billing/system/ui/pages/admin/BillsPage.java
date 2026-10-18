package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.ui.components.CenterLayout;
import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Months;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.PhotoDialog;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Segmented;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/** Every bill, filterable. Bills turn Paid only when SSLCommerz confirms the payment. */
public class BillsPage extends Page {

    private static final String ALL = "All";

    private final DataTable table = new DataTable("Customer", "Meter", "Period", "Units", "Amount", "Due", "Status");
    private final JComboBox<String> meter = Field.combo();
    private final JComboBox<String> month = Field.combo();
    private final JComboBox<String> year = Field.combo();
    private final Segmented status = new Segmented("All", "Unpaid", "Overdue", "Paid");
    private final Txt summary = Txt.muted(" ");
    private final CardLayout states = new CardLayout();
    private final JPanel content = Ui.clear(states);

    private List<Bill> all = List.of();
    private List<Bill> shown = new ArrayList<>();
    private Map<String, String> names = Map.of();
    private boolean loading;

    public BillsPage(AppContext app) {
        super(app);
        Btn print = new Btn("Print", Glyph.PRINTER, Btn.Style.SECONDARY);
        print.addActionListener(e -> {
            try {
                table.print();
            } catch (PrinterException ex) {
                Toast.error(this, "Printing failed: " + ex.getMessage());
            }
        });

        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Bills & payments"), summary), BorderLayout.WEST);
        JPanel actions = Ui.clear(new CenterLayout());
        Btn photo = new Btn("Meter photo", Glyph.GAUGE, Btn.Style.SECONDARY);
        photo.addActionListener(e -> showPhoto());
        actions.add(Ui.hbox(10, photo, print));
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        month.addItem(ALL);
        Months.NAMES.forEach(month::addItem);
        year.addItem(ALL);
        int now = Year.now().getValue();
        for (int y = now; y >= now - 5; y--) {
            year.addItem(String.valueOf(y));
        }
        meter.addItem(ALL);
        for (JComboBox<String> box : List.of(meter, month, year)) {
            box.addActionListener(e -> applyFilters());
            box.setPreferredSize(new Dimension(150, Field.INPUT_HEIGHT));
        }
        status.onChange(i -> applyFilters());
        Ui.width(status, 400);
        JPanel filters = Ui.hbox(14, label("Meter", meter), label("Month", month), label("Year", year), Ui.glue(),
                Ui.vbox(6, Txt.caps("Status"), status));

        table.column(0, new DataTable.NameRenderer(), 220);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.column(4, new DataTable.MoneyRenderer(), 130);
        table.getColumnModel().getColumn(5).setPreferredWidth(120);
        table.column(6, new DataTable.StatusRenderer(), 160);

        Card tableCard = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        tableCard.add(table.scroll(), BorderLayout.CENTER);
        content.add(tableCard, "table");
        content.add(EmptyState.of(Glyph.RECEIPT, "No bills match",
                "Try other filters, or record a bill from Calculate bill."), "empty");

        Card filterCard = new Card(new BorderLayout()).pad(14, 18, 14, 18);
        filterCard.add(filters, BorderLayout.CENTER);
        JPanel body = Ui.clear(new BorderLayout(0, 4));
        body.add(filterCard, BorderLayout.NORTH);
        body.add(content, BorderLayout.CENTER);
        body(body);
    }

    private static JPanel label(String text, JComboBox<String> box) {
        return Ui.vbox(6, Txt.caps(text), box);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, () -> {
            List<Customer> customers = app.customers.findAll();
            return Map.entry(customers, app.billing.search(null, null, null));
        }, data -> {
            names = data.getKey().stream().collect(Collectors.toMap(Customer::meterNo, Customer::name, (a, b) -> a));
            all = data.getValue();
            loading = true;
            Object chosen = meter.getSelectedItem();
            meter.removeAllItems();
            meter.addItem(ALL);
            data.getKey().forEach(c -> meter.addItem(c.meterNo()));
            meter.setSelectedItem(chosen == null ? ALL : chosen);
            loading = false;
            applyFilters();
        });
    }

    private void applyFilters() {
        if (loading) {
            return;
        }
        String m = (String) meter.getSelectedItem();
        String mo = (String) month.getSelectedItem();
        String y = (String) year.getSelectedItem();
        LocalDate today = Dates.today();
        int filter = status.selectedIndex();
        BillStatus s = switch (filter) {
            case 1, 2 -> BillStatus.NOT_PAID;
            case 3 -> BillStatus.PAID;
            default -> null;
        };
        shown = all.stream()
                .filter(b -> m == null || ALL.equals(m) || b.meterNo().equals(m))
                .filter(b -> mo == null || ALL.equals(mo) || b.month().equals(mo))
                .filter(b -> y == null || ALL.equals(y) || String.valueOf(b.year()).equals(y))
                .filter(b -> s == null || (s == BillStatus.PAID) == (b.status() == BillStatus.PAID))
                .filter(b -> filter != 2 || b.isOverdue(today))
                .collect(Collectors.toCollection(ArrayList::new));
        table.clear();
        for (Bill b : shown) {
            table.addRow(names.getOrDefault(b.meterNo(), "Unknown"), b.meterNo(), Fmt.period(b.month(), b.year()),
                    Fmt.number(b.units()), b.amountDue(today), Fmt.date(b.dueDate()), Badge.Label.of(b, today));
        }
        BigDecimal sum = shown.stream().map(b -> b.amountDue(today)).reduce(BigDecimal.ZERO, BigDecimal::add);
        long overdue = shown.stream().filter(b -> b.isOverdue(today)).count();
        summary.setText(shown.size() + (shown.size() == 1 ? " bill" : " bills") + "  ·  " + Fmt.money(sum)
                + " total  ·  " + overdue + " overdue");
        states.show(content, shown.isEmpty() ? "empty" : "table");
    }

    /** The photo of the meter the selected bill was read from. */
    private void showPhoto() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Toast.info(this, "Select a bill first.");
            return;
        }
        Bill bill = shown.get(table.convertRowIndexToModel(row));
        Tasks.run(this, () -> app.billing.photo(bill.meterNo(), bill.month(), bill.year()), photo -> PhotoDialog.show(this,
                "Meter photo · " + names.getOrDefault(bill.meterNo(), bill.meterNo()) + " · " + bill.month() + " " + bill.year(), photo));
    }
}
