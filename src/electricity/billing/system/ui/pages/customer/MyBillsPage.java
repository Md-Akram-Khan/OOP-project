package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.ui.components.CenterLayout;
import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.Period;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/** The customer's full billing history. Double-click a bill to open its invoice. */
public class MyBillsPage extends Page {

    private final DataTable table = new DataTable("Period", "Units", "Amount due", "Due date", "Status");
    private final Txt summary = Txt.muted("Your complete billing history.");
    private final Btn open = new Btn("View invoice", Glyph.FILE_TEXT, Btn.Style.SECONDARY);
    private final Btn pay = new Btn("Pay bill", Glyph.CREDIT_CARD, Btn.Style.PRIMARY);
    private final CardLayout states = new CardLayout();
    private final JPanel content = Ui.clear(states);
    private List<Bill> bills = new ArrayList<>();

    public MyBillsPage(AppContext app) {
        super(app);
        open.setEnabled(false);
        open.addActionListener(e -> openSelected());
        pay.addActionListener(e -> app.go("pay"));

        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("My bills"), summary), BorderLayout.WEST);
        JPanel actions = Ui.clear(new CenterLayout());
        actions.add(Ui.hbox(10, open, pay));
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table.column(0, new DataTable.TextRenderer(javax.swing.SwingConstants.LEFT, true), 220);
        table.column(2, new DataTable.MoneyRenderer(), 160);
        table.column(3, new DataTable.MutedRenderer(), 140);
        table.column(4, new DataTable.StatusRenderer(), 170);
        table.getSelectionModel().addListSelectionListener(e -> open.setEnabled(table.getSelectedRow() >= 0));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });

        Card card = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        card.add(table.scroll(), BorderLayout.CENTER);
        content.add(card, "table");
        content.add(EmptyState.of(Glyph.RECEIPT, "No bills yet",
                "Your bills appear here after each monthly meter reading."), "empty");
        body(content);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.billing::myBills, list -> {
            bills = list;
            LocalDate today = Dates.today();
            table.clear();
            for (Bill b : list) {
                table.addRow(Fmt.period(b.month(), b.year()), Fmt.number(b.units()), b.amountDue(today), Fmt.date(b.dueDate()),
                        Badge.Label.of(b, today));
            }
            BigDecimal open = list.stream().filter(b -> b.status() != BillStatus.PAID)
                    .map(b -> b.amountDue(today)).reduce(BigDecimal.ZERO, BigDecimal::add);
            long late = list.stream().filter(b -> b.isOverdue(today)).count();
            summary.setText(list.size() + (list.size() == 1 ? " bill" : " bills") + "  ·  " + Fmt.money(open) + " outstanding"
                    + (late == 0 ? "" : "  ·  " + late + " overdue"));
            pay.setVisible(list.stream().anyMatch(b -> b.status() == BillStatus.NOT_PAID));
            states.show(content, list.isEmpty() ? "empty" : "table");
        });
    }

    private void openSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Bill bill = bills.get(table.convertRowIndexToModel(row));
        app.go("statement", new Period(bill.month(), bill.year()));
    }
}
