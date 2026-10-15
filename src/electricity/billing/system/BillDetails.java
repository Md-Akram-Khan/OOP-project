package electricity.billing.system;

import electricity.billing.system.client.BillingClient;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/** Customer's bill history, newest first. */
public class BillDetails extends BaseFrame {

    private final BillingClient billing = new BillingClient();

    BillDetails(String meter) {
        super("Bill Details");
        setLayout(new BorderLayout());

        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);

        setVisible(true);

        runAsync(() -> billing.myBills(), bills -> table.setModel(Tables.bills(bills)));
    }
}
