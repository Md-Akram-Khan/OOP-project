package electricity.billing.system;

import electricity.billing.system.client.BillingClient;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import javax.swing.JButton;
import javax.swing.JLabel;

/**
 * Sends the customer to Paytm, then lets them report the payment. Reporting
 * only marks the bill "Pending Verification"; an admin confirms it as Paid.
 */
public class Paytm extends BaseFrame implements ActionListener {

    private static final String PAYMENT_URL = "https://paytm.com/online-payments";

    private final String meter;
    private final String month;
    private final int year;
    private final JButton payNow, paid, back;
    private final BillingClient billing = new BillingClient();

    Paytm(String meter, String month, int year) {
        super("Online Bill Payment", 730, 400);
        this.meter = meter;
        this.month = month;
        this.year = year;
        setLayout(null);

        JLabel heading = new JLabel("Online Bill Payment");
        heading.setFont(new Font("Tahoma", Font.BOLD, 20));
        heading.setBounds(250, 20, 300, 30);
        add(heading);

        JLabel instructions = new JLabel("<html>1. Click <b>Pay Now</b> to pay your " + month + " " + year
                + " bill on Paytm.<br>2. When you've paid, click <b>I've Paid</b>. "
                + "The bill shows as Paid once the admin confirms the payment.</html>");
        instructions.setBounds(50, 65, 620, 70);
        instructions.setFont(new Font("Tahoma", Font.PLAIN, 15));
        add(instructions);

        payNow = new JButton("Pay Now");
        payNow.setBackground(Color.BLUE);
        payNow.setForeground(Color.WHITE);
        payNow.setFont(new Font("Tahoma", Font.BOLD, 16));
        payNow.setBounds(170, 160, 150, 40);
        payNow.addActionListener(this);
        add(payNow);

        paid = new JButton("I've Paid");
        paid.setBackground(new Color(0, 128, 0));
        paid.setForeground(Color.WHITE);
        paid.setFont(new Font("Tahoma", Font.BOLD, 16));
        paid.setBounds(380, 160, 150, 40);
        paid.addActionListener(this);
        add(paid);

        back = darkButton("Back");
        back.setFont(new Font("Tahoma", Font.BOLD, 14));
        back.setBounds(300, 250, 100, 30);
        back.addActionListener(this);
        add(back);

        setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == payNow) {
            try {
                Desktop.getDesktop().browse(new URI(PAYMENT_URL));
            } catch (Exception e) {
                showWarning("Unable to open the payment page. Visit " + PAYMENT_URL + " in your browser.");
            }
        } else if (ae.getSource() == paid) {
            runAsync(() -> {
                billing.submitPayment(month, year);
                return null;
            }, done -> {
                showInfo("Thanks! Your payment is waiting for the admin to confirm it.");
                dispose();
            }, payNow, paid, back);
        } else {
            dispose();
            new PayBill(meter);
        }
    }
}
