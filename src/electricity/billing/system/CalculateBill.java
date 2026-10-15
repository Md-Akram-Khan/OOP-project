package electricity.billing.system;

import electricity.billing.system.client.BillingClient;
import electricity.billing.system.client.CustomerClient;
import java.awt.BorderLayout;
import java.awt.Choice;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Admin enters a meter reading; the bill is priced with that meter's tariff and saved. */
public class CalculateBill extends BaseFrame implements ActionListener {

    private final JPanel p;
    private final Choice meterNumber, month, year;
    private final JLabel name, address;
    private final JTextField units;
    private final JButton submit, cancel;
    private final CustomerClient customers = new CustomerClient();
    private final BillingClient billing = new BillingClient();

    CalculateBill() {
        super("Calculate Bill");
        setLayout(new BorderLayout());

        p = new JPanel();
        p.setLayout(null);
        p.setBackground(PANEL_BLUE);

        JLabel heading = new JLabel("Calculate Electricity Bill");
        heading.setBounds(100, 10, 400, 25);
        heading.setFont(new Font("Tahoma", Font.PLAIN, 24));
        p.add(heading);

        addLabel("Meter Number", 80);
        meterNumber = new Choice();
        meterNumber.setBounds(170, 80, 200, 20);
        p.add(meterNumber);

        addLabel("Name", 120);
        name = new JLabel("");
        name.setBounds(170, 120, 200, 20);
        p.add(name);

        addLabel("Address", 160);
        address = new JLabel("");
        address.setBounds(170, 160, 200, 20);
        p.add(address);

        addLabel("Units Consumed", 200);
        units = new JTextField();
        units.setBounds(170, 200, 200, 20);
        p.add(units);

        addLabel("Month", 240);
        month = monthChoice();
        month.setBounds(170, 240, 200, 20);
        p.add(month);

        addLabel("Year", 280);
        year = yearChoice();
        year.setBounds(170, 280, 200, 20);
        p.add(year);

        submit = darkButton("Submit");
        submit.setBounds(100, 350, 100, 25);
        submit.addActionListener(this);
        p.add(submit);

        cancel = darkButton("Cancel");
        cancel.setBounds(230, 350, 100, 25);
        cancel.addActionListener(this);
        p.add(cancel);

        add(p, BorderLayout.CENTER);
        add(new JLabel(icon("CalculateBill.jpg")), BorderLayout.WEST);

        meterNumber.addItemListener(e -> showCustomer(meterNumber.getSelectedItem()));
        setVisible(true);

        runAsync(customers::meterNumbers, meters -> {
            meters.forEach(meterNumber::add);
            if (meters.isEmpty()) {
                showWarning("There are no customers yet. Add a customer first.");
            } else {
                showCustomer(meters.get(0));
            }
        }, submit);
    }

    private void addLabel(String text, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(30, y, 120, 20);
        p.add(label);
    }

    private void showCustomer(String meter) {
        name.setText("");
        address.setText("");
        runAsync(() -> customers.find(meter), c -> {
            // Ignore the answer if the admin has already picked another meter.
            if (meter.equals(meterNumber.getSelectedItem())) {
                name.setText(c.name());
                address.setText(c.address());
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != submit) {
            dispose();
            return;
        }
        String meter = meterNumber.getSelectedItem();
        if (meter == null) {
            showWarning("Select a meter number.");
            return;
        }
        int unitCount;
        try {
            unitCount = Integer.parseInt(units.getText().trim());
        } catch (NumberFormatException e) {
            showWarning("Enter the units consumed as a whole number.");
            return;
        }
        if (unitCount < 0) {
            showWarning("Units consumed cannot be negative.");
            return;
        }
        String billMonth = month.getSelectedItem();
        int billYear = Integer.parseInt(year.getSelectedItem());
        runAsync(() -> billing.createBill(meter, billMonth, billYear, unitCount), bill -> {
            showInfo("Bill saved for " + bill.month() + " " + bill.year()
                    + ".\nTotal: Rs. " + bill.total());
            dispose();
        }, submit, cancel);
    }
}
