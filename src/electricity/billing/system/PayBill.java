package electricity.billing.system;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.client.BillingClient;
import electricity.billing.system.client.CustomerClient;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;

/** Customer views a month's bill and starts paying it. */
public class PayBill extends BaseFrame implements ActionListener {

    private static final Color PAID_GREEN = new Color(0, 128, 0);
    private static final Color PENDING_ORANGE = new Color(200, 110, 0);

    private final String meter;
    private final Choice month, year;
    private final JLabel name, units, total, status;
    private final JButton pay, back;
    private final CustomerClient customers = new CustomerClient();
    private final BillingClient billing = new BillingClient();
    private Bill currentBill;

    PayBill(String meter) {
        super("Pay Bill");
        this.meter = meter;
        setLayout(null);

        JLabel heading = new JLabel("Electricity Bill");
        heading.setFont(new Font("Tahoma", Font.BOLD, 24));
        heading.setBounds(120, 30, 400, 30);
        add(heading);

        addLabel("Meter Number", 80);
        JLabel meternumber = value(80);
        meternumber.setText(meter);

        addLabel("Name", 120);
        name = value(120);

        addLabel("Month", 160);
        month = monthChoice();
        month.setBounds(170, 160, 100, 20);
        add(month);
        year = yearChoice();
        year.setBounds(280, 160, 80, 20);
        add(year);

        addLabel("Units", 200);
        units = value(200);

        addLabel("Total Bill", 240);
        total = value(240);

        addLabel("Status", 280);
        status = value(280);

        month.addItemListener(e -> loadBill());
        year.addItemListener(e -> loadBill());

        pay = darkButton("Pay");
        pay.setBounds(100, 350, 100, 25);
        pay.addActionListener(this);
        add(pay);

        back = darkButton("Back");
        back.setBounds(230, 350, 100, 25);
        back.addActionListener(this);
        add(back);

        JLabel image = new JLabel(icon("PayBill.jpg"));
        image.setBounds(400, 100, 300, 300);
        add(image);

        setVisible(true);

        runAsync(customers::myProfile, c -> name.setText(c.name()));
        loadBill();
    }

    private void addLabel(String text, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(55, y, 110, 20);
        add(label);
    }

    private JLabel value(int y) {
        JLabel label = new JLabel("");
        label.setBounds(170, y, 220, 20);
        add(label);
        return label;
    }

    private void loadBill() {
        String billMonth = month.getSelectedItem();
        int billYear = Integer.parseInt(year.getSelectedItem());
        // Clear the old month's figures straight away so they're never shown against the new month.
        currentBill = null;
        units.setText("");
        total.setText("");
        status.setForeground(Color.DARK_GRAY);
        status.setText("Loading...");
        runAsync(() -> billing.myBill(billMonth, billYear), found -> {
            if (!billMonth.equals(month.getSelectedItem())
                    || billYear != Integer.parseInt(year.getSelectedItem())) {
                return;
            }
            currentBill = found.orElse(null);
            if (currentBill == null) {
                status.setText("No bill for this month");
                return;
            }
            units.setText(String.valueOf(currentBill.units()));
            total.setText("Rs. " + currentBill.total());
            status.setText(currentBill.status().label());
            status.setForeground(switch (currentBill.status()) {
                case PAID -> PAID_GREEN;
                case PENDING -> PENDING_ORANGE;
                case NOT_PAID -> Color.RED;
            });
        });
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != pay) {
            dispose();
            return;
        }
        if (currentBill == null) {
            showWarning("There's no bill for the selected month.");
        } else if (currentBill.status() != BillStatus.NOT_PAID) {
            showWarning("This bill is already " + currentBill.status().label().toLowerCase() + ".");
        } else {
            dispose();
            new Paytm(meter, currentBill.month(), currentBill.year());
        }
    }
}
