package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.model.Months;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.InvoiceView;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons.Glyph;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.print.PrinterException;
import java.time.Year;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/** A month's bill as a printable invoice. */
public class StatementPage extends Page {

    private final JComboBox<String> month = Field.combo();
    private final JComboBox<Integer> year = Field.combo();
    private final InvoiceView invoice = new InvoiceView();
    private boolean loading;

    public StatementPage(AppContext app) {
        super(app);
        Months.NAMES.forEach(month::addItem);
        int now = Year.now().getValue();
        for (int y = now; y >= now - 5; y--) {
            year.addItem(y);
        }
        month.setPreferredSize(new Dimension(150, Field.INPUT_HEIGHT));
        year.setPreferredSize(new Dimension(100, Field.INPUT_HEIGHT));
        month.addActionListener(e -> load());
        year.addActionListener(e -> load());

        Btn print = new Btn("Print", Glyph.PRINTER, Btn.Style.PRIMARY);
        print.addActionListener(e -> {
            try {
                invoice.print();
            } catch (PrinterException ex) {
                Toast.error(this, "Printing failed: " + ex.getMessage());
            }
        });
        add(header("Invoice", "Choose a month to view or print its bill.", month, year, print), BorderLayout.NORTH);

        JPanel center = Ui.clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
        center.add(invoice);
        body(center);
    }

    @Override
    public void onShow(Object param) {
        Period period = param instanceof Period p ? p : null;
        if (period != null) {
            loading = true;
            month.setSelectedItem(period.month());
            year.setSelectedItem(period.year());
            loading = false;
        }
        load();
    }

    private void load() {
        if (loading || month.getSelectedItem() == null || year.getSelectedItem() == null) {
            return;
        }
        String m = (String) month.getSelectedItem();
        int y = (Integer) year.getSelectedItem();
        Tasks.run(this, () -> app.billing.myStatement(m, y), statement -> invoice.show(statement, m, y));
    }
}
