package electricity.billing.system;

import electricity.billing.system.client.BillingClient;
import electricity.billing.system.client.CustomerClient;
import java.awt.Choice;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PrinterException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/** Admin searches every bill and confirms payments. */
public class DepositDetails extends BaseFrame implements ActionListener {

    private static final String ALL = "All";

    private final Choice meternumber, cmonth, cyear;
    private final JTable table;
    private final JButton search, print, markPaid;
    private final CustomerClient customers = new CustomerClient();
    private final BillingClient billing = new BillingClient();

    DepositDetails() {
        super("Deposit Details");
        setLayout(null);

        addLabel("Meter Number", 20, 100);
        meternumber = new Choice();
        meternumber.add(ALL);
        meternumber.setBounds(120, 20, 120, 20);
        add(meternumber);

        addLabel("Month", 260, 50);
        cmonth = monthChoice();
        cmonth.insert(ALL, 0);
        cmonth.select(ALL);
        cmonth.setBounds(310, 20, 120, 20);
        add(cmonth);

        addLabel("Year", 450, 40);
        cyear = yearChoice();
        cyear.insert(ALL, 0);
        cyear.select(ALL);
        cyear.setBounds(490, 20, 100, 20);
        add(cyear);

        search = new JButton("Search");
        search.setBounds(20, 60, 90, 25);
        search.addActionListener(this);
        add(search);

        print = new JButton("Print");
        print.setBounds(120, 60, 90, 25);
        print.addActionListener(this);
        add(print);

        markPaid = new JButton("Mark Selected as Paid");
        markPaid.setBounds(220, 60, 180, 25);
        markPaid.addActionListener(this);
        add(markPaid);

        table = new JTable();
        JScrollPane sp = new JScrollPane(table);
        sp.setBounds(0, 100, 715, 410);
        add(sp);

        setVisible(true);

        runAsync(customers::meterNumbers, meters -> meters.forEach(meternumber::add));
        runSearch();
    }

    private void addLabel(String text, int x, int width) {
        JLabel label = new JLabel(text);
        label.setBounds(x, 20, width, 20);
        add(label);
    }

    private static String filter(Choice choice) {
        String value = choice.getSelectedItem();
        return ALL.equals(value) ? null : value;
    }

    private void runSearch() {
        String meter = filter(meternumber);
        String month = filter(cmonth);
        String yearText = filter(cyear);
        Integer year = yearText == null ? null : Integer.valueOf(yearText);
        runAsync(() -> billing.search(meter, month, year),
                bills -> table.setModel(Tables.bills(bills)),
                search, markPaid);
    }

    private void markSelectedPaid() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showWarning("Select a bill in the table first.");
            return;
        }
        String meter = (String) table.getValueAt(row, Tables.BILL_METER);
        String month = (String) table.getValueAt(row, Tables.BILL_MONTH);
        int year = (Integer) table.getValueAt(row, Tables.BILL_YEAR);
        int answer = JOptionPane.showConfirmDialog(this,
                "Mark meter " + meter + "'s " + month + " " + year + " bill as Paid?",
                getTitle(), JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        runAsync(() -> {
            billing.confirmPayment(meter, month, year);
            return null;
        }, done -> runSearch(), search, markPaid);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == search) {
            runSearch();
        } else if (ae.getSource() == markPaid) {
            markSelectedPaid();
        } else if (ae.getSource() == print) {
            try {
                table.print();
            } catch (PrinterException e) {
                showError(e);
            }
        }
    }
}
