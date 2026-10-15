package electricity.billing.system;

import electricity.billing.system.service.CustomerService;
import java.awt.BorderLayout;
import java.awt.print.PrinterException;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/** Admin's list of every customer. */
public class CustomerDetails extends BaseFrame {

    private final CustomerService customers = new CustomerService();

    CustomerDetails() {
        super("Customer Details");
        setLayout(new BorderLayout());

        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton print = new JButton("Print");
        print.addActionListener(e -> {
            try {
                table.print();
            } catch (PrinterException ex) {
                showError(ex);
            }
        });
        add(print, BorderLayout.SOUTH);

        setVisible(true);

        runAsync(customers::findAll, list -> table.setModel(Tables.customers(list)), print);
    }
}
