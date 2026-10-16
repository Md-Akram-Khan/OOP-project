package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.model.BillExplanation;
import electricity.billing.system.model.Months;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.InvoiceView;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.time.Year;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/** A month's bill as a printable invoice. */
public class StatementPage extends Page {

    private final JComboBox<String> month = Field.combo();
    private final JComboBox<Integer> year = Field.combo();
    private final InvoiceView invoice = new InvoiceView();
    private final Card why = new Card(new BorderLayout(0, 14)).pad(24, 26, 24, 26);
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
        JPanel whyRow = Ui.clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
        whyRow.add(Ui.fixedWidth(why, 760));
        why.setVisible(false);
        body(Ui.vbox(16, center, whyRow));
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
        Tasks.run(this, () -> app.billing.myStatement(m, y), statement -> {
            invoice.show(statement, m, y);
            explain(statement.explanation());
        });
    }

    /** "Why is this bill different?": the change since the last bill, split into its causes. */
    private void explain(BillExplanation e) {
        why.removeAll();
        if (e == null) {
            why.setVisible(false);
            revalidate();
            return;
        }
        BigDecimal diff = e.difference();
        String change = diff.signum() == 0 ? "the same as" : Fmt.money(diff.abs()) + (diff.signum() > 0 ? " more than" : " less than");
        why.add(Ui.vbox(4, Ui.hbox(10, new IconTile(Glyph.INFO, 36, Theme.VIOLET), Txt.h2("Why is this bill different?")),
                Txt.muted("This bill is " + change + " your " + e.comparedWith() + " bill (" + Fmt.money(e.previousTotal()) + ").")),
                BorderLayout.NORTH);
        JPanel rows = Ui.vbox(12);
        for (BillExplanation.Reason r : e.reasons()) {
            JPanel row = Ui.clear(new BorderLayout(16, 0));
            row.add(Ui.vbox(3, Txt.h3(r.title()), Txt.small(r.detail()).wrap(560)), BorderLayout.CENTER);
            if (r.amount() != null) {
                String sign = r.amount().signum() > 0 ? "+" : r.amount().signum() < 0 ? "−" : "";
                Txt amount = new Txt(sign + Fmt.money(r.amount().abs()), Theme.semibold(15),
                        () -> r.amount().signum() > 0 ? Theme.DANGER : r.amount().signum() < 0 ? Theme.SUCCESS : Theme.muted());
                row.add(Ui.top(amount), BorderLayout.EAST);
            }
            rows.add(Ui.left(row));
        }
        why.add(rows, BorderLayout.CENTER);
        why.setVisible(true);
        revalidate();
        repaint();
    }
}
