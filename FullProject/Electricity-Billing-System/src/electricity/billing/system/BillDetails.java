package electricity.billing.system;

import electricity.billing.system.service.BillingService;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/** Customer's bill history, newest first. */
public class BillDetails extends BaseFrame {

    private final BillingService billing = new BillingService();

    BillDetails(String meter) {
        super("Bill Details");
        setLayout(new BorderLayout());

        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);

        setVisible(true);

        runAsync(() -> billing.billsFor(meter), bills -> table.setModel(Tables.bills(bills)));
    }
}
