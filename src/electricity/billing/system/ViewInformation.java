package electricity.billing.system;

import electricity.billing.system.model.Customer;
import electricity.billing.system.service.CustomerService;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Customer's read-only profile. */
public class ViewInformation extends BaseFrame {

    private final JPanel infoPanel;
    private final CustomerService customers = new CustomerService();

    ViewInformation(String meter) {
        super("Customer Profile");
        setLayout(null);

        JLabel image = new JLabel(icon("ViewInformation.jpg", 280, 280));
        image.setBounds(30, 100, 300, 300);
        add(image);

        infoPanel = new JPanel();
        infoPanel.setBounds(310, 50, 400, 350);
        infoPanel.setLayout(null);
        infoPanel.setBackground(Color.WHITE);
        add(infoPanel);

        JLabel heading = new JLabel("Customer Information");
        heading.setBounds(90, 10, 300, 30);
        heading.setFont(new Font("Tahoma", Font.BOLD, 18));
        infoPanel.add(heading);

        JLabel name = row("Name:", 50);
        JLabel meternumber = row("Meter No:", 80);
        JLabel address = row("Address:", 110);
        JLabel city = row("City:", 140);
        JLabel state = row("State:", 170);
        JLabel email = row("Email:", 200);
        JLabel phone = row("Phone:", 230);

        JButton cancel = darkButton("Cancel");
        cancel.setBounds(420, 320, 100, 30);
        cancel.addActionListener(e -> dispose());
        add(cancel);

        setVisible(true);

        runAsync(() -> customers.find(meter), found -> {
            if (found.isEmpty()) {
                showWarning("No customer details were found for meter " + meter + ".");
                return;
            }
            Customer c = found.get();
            name.setText(c.name());
            meternumber.setText(c.meterNo());
            address.setText(c.address());
            city.setText(c.city());
            state.setText(c.state());
            email.setText(c.email());
            phone.setText(c.phone());
        });
    }

    private JLabel row(String label, int y) {
        JLabel lbl = new JLabel(label);
        lbl.setBounds(20, y, 100, 20);
        infoPanel.add(lbl);
        JLabel value = new JLabel("");
        value.setBounds(150, y, 240, 20);
        infoPanel.add(value);
        return value;
    }
}
