package electricity.billing.system;

import electricity.billing.system.model.Customer;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Validation;
import electricity.billing.system.client.CustomerClient;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Customer edits their contact details. Name and meter number are fixed. */
public class UpdateInformation extends BaseFrame implements ActionListener {

    private final String meter;
    private final JLabel name, meternumber;
    private final JTextField tfaddress, tfcity, tfstate, tfemail, tfphone;
    private final JButton update, cancel;
    private final CustomerClient customers = new CustomerClient();
    private Customer loaded;

    UpdateInformation(String meter) {
        super("Update Information");
        this.meter = meter;
        setLayout(null);

        JLabel heading = new JLabel("UPDATE CUSTOMER INFORMATION");
        heading.setBounds(210, 30, 400, 30);
        heading.setFont(new Font("Tahoma", Font.PLAIN, 20));
        add(heading);

        addLabel("Name", 70);
        name = new JLabel("");
        name.setBounds(170, 70, 200, 20);
        add(name);

        addLabel("Meter Number", 110);
        meternumber = new JLabel("");
        meternumber.setBounds(170, 110, 200, 20);
        add(meternumber);

        tfaddress = addField("Address", 150);
        tfcity = addField("City", 190);
        tfstate = addField("State", 230);
        tfemail = addField("Email", 270);
        tfphone = addField("Phone", 310);

        update = darkButton("Update");
        update.setBounds(70, 360, 100, 25);
        update.addActionListener(this);
        add(update);

        cancel = darkButton("Cancel");
        cancel.setBounds(230, 360, 100, 25);
        cancel.addActionListener(this);
        add(cancel);

        JLabel image = new JLabel(icon("UpdateInformation.jpg", 300, 300));
        image.setBounds(350, 90, 400, 300);
        add(image);

        setVisible(true);

        runAsync(customers::myProfile, found -> {
            loaded = found;
            name.setText(loaded.name());
            meternumber.setText(loaded.meterNo());
            tfaddress.setText(loaded.address());
            tfcity.setText(loaded.city());
            tfstate.setText(loaded.state());
            tfemail.setText(loaded.email());
            tfphone.setText(loaded.phone());
        }, update);
    }

    private void addLabel(String text, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(30, y, 100, 20);
        add(label);
    }

    private JTextField addField(String label, int y) {
        addLabel(label, y);
        JTextField field = new JTextField();
        field.setBounds(170, y, 200, 20);
        add(field);
        return field;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() != update) {
            dispose();
            return;
        }
        if (loaded == null) {
            showWarning("Your details haven't loaded yet.");
            return;
        }
        Customer changed = new Customer(meter, loaded.name(),
                tfaddress.getText().trim(),
                tfcity.getText().trim(),
                tfstate.getText().trim(),
                tfemail.getText().trim(),
                tfphone.getText().trim());
        try {
            Validation.customer(changed);
        } catch (AppException e) {
            showWarning(e.getMessage());
            return;
        }
        runAsync(() -> {
            customers.updateMyContact(changed);
            return null;
        }, done -> {
            showInfo("Your information has been updated.");
            dispose();
        }, update, cancel);
    }
}
