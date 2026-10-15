package electricity.billing.system;

import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import electricity.billing.system.client.AuthClient;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.concurrent.Callable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

/**
 * Account creation.
 *
 * From the login screen: customers claim the account reserved for their
 * meter by giving their meter number and registered email. "Admin" is only
 * offered while no admin exists yet, for first-time set-up.
 *
 * From the admin dashboard ({@code createdBy} is that admin): creates
 * another admin account.
 */
public class Signup extends BaseFrame implements ActionListener {

    private final UserAccount createdBy;
    private final AuthClient auth = new AuthClient();
    private final JPanel panel;
    private final Choice accountType;
    private final JLabel lblmeter, lblemail, lblname;
    private final JTextField meter, email, name, username;
    private final JPasswordField password, confirm;
    private final JButton create, back;

    Signup(UserAccount createdBy) {
        super(createdBy == null ? "Electricity Billing System" : "Add Admin");
        this.createdBy = createdBy;
        setLayout(null);

        panel = new JPanel();
        panel.setBounds(20, 20, 680, 470);
        panel.setBorder(new TitledBorder(new LineBorder(PANEL_BLUE, 2),
                createdBy == null ? "Sign up" : "Add Admin",
                TitledBorder.LEADING, TitledBorder.TOP, null, HEADING_BLUE));
        panel.setBackground(Color.WHITE);
        panel.setLayout(null);
        add(panel);

        addLabel("Create Account As", 30);
        accountType = new Choice();
        if (createdBy != null) {
            accountType.add(Role.ADMIN.label());
        } else {
            accountType.add(Role.CUSTOMER.label());
        }
        accountType.setBounds(250, 30, 200, 30);
        panel.add(accountType);

        lblmeter = addLabel("Meter Number", 80);
        meter = addField(new JTextField(), 80);

        // Customers prove who they are with their email; admins type their name. Same row, one shown at a time.
        lblemail = addLabel("Registered Email", 130);
        email = addField(new JTextField(), 130);
        lblname = addLabel("Name", 130);
        name = addField(new JTextField(), 130);

        addLabel("Username", 180);
        username = addField(new JTextField(), 180);

        addLabel("Password", 230);
        password = (JPasswordField) addField(new JPasswordField(), 230);

        addLabel("Confirm Password", 280);
        confirm = (JPasswordField) addField(new JPasswordField(), 280);

        create = darkButton("Create");
        create.setBounds(100, 340, 150, 40);
        create.addActionListener(this);
        panel.add(create);

        back = darkButton("Back");
        back.setBounds(300, 340, 150, 40);
        back.addActionListener(this);
        panel.add(back);

        JLabel image = new JLabel(icon("Signup.jpg"));
        image.setBounds(460, 70, 200, 300);
        panel.add(image);

        accountType.addItemListener(e -> updateFields());
        updateFields();
        setVisible(true);

        if (createdBy == null) {
            runAsync(auth::adminExists, exists -> {
                if (!exists) {
                    accountType.add(Role.ADMIN.label());
                }
            });
        }
    }

    private JLabel addLabel(String text, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(40, y, 200, 30);
        label.setForeground(Color.DARK_GRAY);
        label.setFont(new Font("Tahoma", Font.BOLD, 16));
        panel.add(label);
        return label;
    }

    private JTextField addField(JTextField field, int y) {
        field.setBounds(250, y, 200, 30);
        panel.add(field);
        return field;
    }

    private Role selectedRole() {
        return Role.fromLabel(accountType.getSelectedItem());
    }

    private void updateFields() {
        boolean customer = selectedRole() == Role.CUSTOMER;
        lblmeter.setVisible(customer);
        meter.setVisible(customer);
        lblemail.setVisible(customer);
        email.setVisible(customer);
        lblname.setVisible(!customer);
        name.setVisible(!customer);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == back) {
            close();
            return;
        }
        char[] pass = password.getPassword();
        if (!Arrays.equals(pass, confirm.getPassword())) {
            showWarning("The passwords don't match.");
            return;
        }
        String user = username.getText().trim();
        Callable<Void> task;
        if (selectedRole() == Role.CUSTOMER) {
            String meterNo = meter.getText().trim();
            String mail = email.getText().trim();
            task = () -> {
                auth.registerCustomer(meterNo, mail, user, pass);
                return null;
            };
        } else {
            String adminName = name.getText().trim();
            task = () -> {
                auth.createAdmin(user, adminName, pass);
                return null;
            };
        }
        runAsync(task, done -> {
            showInfo("Account created successfully.");
            close();
        }, create, back);
    }

    private void close() {
        dispose();
        if (createdBy == null) {
            new Login();
        }
    }
}
