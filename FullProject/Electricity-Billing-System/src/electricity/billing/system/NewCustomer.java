package electricity.billing.system;

import electricity.billing.system.model.Customer;
import electricity.billing.system.service.AppException;
import electricity.billing.system.service.CustomerService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Admin adds a customer. The meter number is generated when the customer is saved. */
public class NewCustomer extends BaseFrame implements ActionListener {

    private final JPanel p;
    private final JTextField tfname, tfaddress, tfcity, tfstate, tfemail, tfphone;
    private final JButton next, cancel;
    private final CustomerService customers = new CustomerService();

    NewCustomer() {
        super("Add Customer");
        setLayout(new BorderLayout());

        p = new JPanel();
        p.setLayout(null);
        p.setBackground(PANEL_BLUE);

        JLabel heading = new JLabel("New Customer");
        heading.setBounds(140, 40, 200, 25);
        heading.setFont(new Font("Tahoma", Font.PLAIN, 24));
        p.add(heading);

        tfname = addField("Customer Name", 80);

        JLabel lblmeterno = new JLabel("Meter Number");
        lblmeterno.setBounds(50, 120, 100, 20);
        p.add(lblmeterno);
        JLabel lblmeter = new JLabel("Assigned when you click Next");
        lblmeter.setBounds(190, 120, 220, 20);
        lblmeter.setFont(lblmeter.getFont().deriveFont(Font.ITALIC));
        lblmeter.setForeground(Color.DARK_GRAY);
        p.add(lblmeter);

        tfaddress = addField("Address", 160);
        tfcity = addField("City", 200);
        tfstate = addField("State", 240);
        tfemail = addField("Email", 280);
        tfphone = addField("Phone Number", 320);

        next = darkButton("Next");
        next.setBounds(100, 390, 100, 25);
        next.addActionListener(this);
        p.add(next);

        cancel = darkButton("Cancel");
        cancel.setBounds(230, 390, 100, 25);
        cancel.addActionListener(this);
        p.add(cancel);

        add(p, BorderLayout.CENTER);
        add(new JLabel(icon("NewCustomer.jpg")), BorderLayout.WEST);
        setVisible(true);
    }

    private JTextField addField(String label, int y) {
        JLabel lbl = new JLabel(label);
        lbl.setBounds(50, y, 100, 20);
        p.add(lbl);
        JTextField field = new JTextField();
        field.setBounds(190, y, 200, 20);
        p.add(field);
        return field;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != next) {
            dispose();
            return;
        }
        Customer draft = new Customer(null,
                tfname.getText().trim(),
                tfaddress.getText().trim(),
                tfcity.getText().trim(),
                tfstate.getText().trim(),
                tfemail.getText().trim(),
                tfphone.getText().trim());
        try {
            CustomerService.validate(draft);
        } catch (AppException e) {
            showWarning(e.getMessage());
            return;
        }
        runAsync(() -> customers.create(draft), saved -> {
            showInfo("Customer added.\nMeter number: " + saved.meterNo()
                    + "\n\nGive the customer this meter number so they can sign up.");
            dispose();
            new MeterInfo(saved.meterNo());
        }, next, cancel);
    }
}
