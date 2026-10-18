package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.ui.components.CenterLayout;
import com.formdev.flatlaf.FlatClientProperties;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.print.PrinterException;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/** Every customer, searchable as you type. */
public class CustomersPage extends Page {

    private final DataTable table = new DataTable("Name", "Meter no.", "City", "Email", "Phone");
    private final JTextField search = new JTextField();
    private final Txt count = Txt.muted("Everyone connected to your grid.");
    private final CardLayout states = new CardLayout();
    private final JPanel content = Ui.clear(states);

    public CustomersPage(AppContext app) {
        super(app);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search name, meter, city…");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.of(Glyph.SEARCH, 17, Theme::muted));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.MINIMUM_HEIGHT, Field.INPUT_HEIGHT - 2);
        search.setPreferredSize(new Dimension(280, Field.INPUT_HEIGHT - 2));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });

        Btn print = new Btn("Print", Glyph.PRINTER, Btn.Style.SECONDARY);
        print.addActionListener(e -> {
            try {
                table.print();
            } catch (PrinterException ex) {
                Toast.error(this, "Printing failed: " + ex.getMessage());
            }
        });
        Btn meterNumber = new Btn("Meter no. on meter", Glyph.HASH, Btn.Style.SECONDARY);
        meterNumber.setToolTipText("Set the number printed on the selected customer's meter, so photos of it are recognised");
        meterNumber.addActionListener(e -> editPrintedNumber());
        Btn add = new Btn("Add customer", Glyph.USER_PLUS, Btn.Style.PRIMARY);
        add.addActionListener(e -> app.go("add-customer"));

        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Customers"), count), BorderLayout.WEST);
        JPanel actions = Ui.clear(new CenterLayout());
        actions.add(Ui.hbox(10, search, meterNumber, print, add));
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table.column(0, new DataTable.NameRenderer(), 240);
        table.column(1, new DataTable.TextRenderer(javax.swing.SwingConstants.LEFT, true), 110);
        table.column(3, new DataTable.MutedRenderer(), 220);
        table.column(4, new DataTable.MutedRenderer(), 150);

        Card card = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        card.add(table.scroll(), BorderLayout.CENTER);
        Btn first = new Btn("Add your first customer", Glyph.USER_PLUS, Btn.Style.PRIMARY);
        first.addActionListener(e -> app.go("add-customer"));
        content.add(card, "table");
        content.add(EmptyState.of(Glyph.USERS, "No customers yet",
                "Add a customer to generate their meter number and start billing.", first), "empty");
        body(content);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.customers::findAll, this::render);
    }

    private void render(List<Customer> customers) {
        table.clear();
        for (Customer c : customers) {
            table.addRow(c.name(), c.meterNo(), c.city(), c.email(), c.phone());
        }
        count.setText(customers.size() + (customers.size() == 1 ? " customer" : " customers") + " connected to your grid.");
        states.show(content, customers.isEmpty() ? "empty" : "table");
        filter();
    }

    @SuppressWarnings("unchecked")
    private void filter() {
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();
        String text = search.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    /**
     * Sets the number printed on the selected customer's meter. Customers added
     * before meter numbers were recorded need it once, so the Calculate bill
     * page can recognise their meter in a photo.
     */
    private void editPrintedNumber() {
        Object selected = table.selected(1);
        if (selected == null) {
            Toast.info(this, "Select a customer first.");
            return;
        }
        String meterNo = selected.toString();
        Tasks.run(this, () -> app.billing.lastReading(meterNo), m -> {
            String current = m.serialNo() == null ? "" : m.serialNo();
            Object typed = JOptionPane.showInputDialog(this,
                    "Meter number exactly as printed on customer " + meterNo + "'s meter:",
                    "Meter number on the meter", JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (typed == null || typed.toString().trim().isEmpty() || typed.toString().trim().equals(current)) {
                return;
            }
            String value = typed.toString().trim();
            Tasks.run(this, () -> {
                app.customers.saveMeter(new Meter(meterNo, m.meterType(), m.billType(), Meter.DEFAULT_BILLING_DAYS, m.loadKw(), value));
                return null;
            }, done -> Toast.success(this, "Saved. Photos of meter " + value + " will now be recognised as customer " + meterNo + "."));
        });
    }
}
