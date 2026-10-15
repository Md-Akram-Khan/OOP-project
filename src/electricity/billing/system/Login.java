package electricity.billing.system;

import electricity.billing.system.model.Role;
import electricity.billing.system.client.AuthClient;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

public class Login extends BaseFrame implements ActionListener {

    private final JButton login, cancel, signup;
    private final JTextField username;
    private final JPasswordField password;
    private final Choice role;
    private final AuthClient auth = new AuthClient();

    Login() {
        super("Electricity Billing System");
        setLayout(null);

        JPanel panel = new JPanel();
        panel.setBounds(20, 20, 680, 470);
        panel.setBorder(new TitledBorder(new LineBorder(PANEL_BLUE, 2), "Login",
                TitledBorder.LEADING, TitledBorder.TOP, null, HEADING_BLUE));
        panel.setBackground(Color.WHITE);
        panel.setLayout(null);
        add(panel);

        panel.add(label("Username:", 80));
        username = new JTextField();
        username.setBounds(440, 80, 200, 30);
        panel.add(username);

        panel.add(label("Password:", 130));
        password = new JPasswordField();
        password.setBounds(440, 130, 200, 30);
        panel.add(password);

        panel.add(label("Login as:", 180));
        role = new Choice();
        role.add(Role.ADMIN.label());
        role.add(Role.CUSTOMER.label());
        role.setBounds(440, 180, 200, 30);
        panel.add(role);

        login = new JButton("Login", icon("login.png", 20, 20));
        login.setBounds(320, 250, 100, 30);
        login.addActionListener(this);
        panel.add(login);

        cancel = new JButton("Cancel", icon("cancel.png"));
        cancel.setBounds(440, 250, 100, 30);
        cancel.addActionListener(this);
        panel.add(cancel);

        signup = new JButton("Signup", icon("signup.png", 20, 20));
        signup.setBounds(560, 250, 100, 30);
        signup.addActionListener(this);
        panel.add(signup);

        JLabel image = new JLabel(icon("Login.jpg"));
        image.setBounds(20, 80, 300, 300);
        panel.add(image);

        getRootPane().setDefaultButton(login);
        setVisible(true);
    }

    private static JLabel label(String text, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(340, y, 100, 30);
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        return label;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == login) {
            String user = username.getText();
            char[] pass = password.getPassword();
            Role selectedRole = Role.fromLabel(role.getSelectedItem());
            password.setText("");
            runAsync(() -> auth.login(user, pass, selectedRole), account -> {
                dispose();
                new Project(account);
            }, login, signup);
        } else if (ae.getSource() == cancel) {
            dispose();
        } else if (ae.getSource() == signup) {
            dispose();
            new Signup(null);
        }
    }
}
