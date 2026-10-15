package electricity.billing.system;

import electricity.billing.system.SystemTools.Tool;
import electricity.billing.system.model.UserAccount;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JLabel;

/** Dashboard shown after login. Admins and customers get different menus. */
public class Project extends BaseFrame implements ActionListener {

    private final UserAccount account;

    Project(UserAccount account) {
        super(account.isAdmin() ? "Admin Profile" : "Customer Profile");
        this.account = account;
        setLayout(null);

        // Closing the dashboard closes every window it opened and exits the app.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAllWindows();
            }
        });

        if (account.isAdmin()) {
            section("Master", 200, 50);
            menuButton("Add New Customer", 80);
            menuButton("Customer Details", 120);
            menuButton("Deposit Details", 160);
            menuButton("Calculate Bill", 200);
            menuButton("Add Admin", 240);
            section("Utility", 205, 280);
            menuButton("Notepad", 310);
            menuButton("Calculator", 350);
            section("Exit", 210, 390);
            menuButton("Log out", 420);
            image("project1.jpg", 420);
        } else {
            section("Information", 180, 70);
            menuButton("Update Information", 100);
            menuButton("View Information", 140);
            section("User", 205, 170);
            menuButton("Pay Bill", 200);
            menuButton("Bill Details", 240);
            menuButton("Generate Bill", 280);
            section("Utility", 202, 310);
            menuButton("Notepad", 340);
            menuButton("Calculator", 380);
            menuButton("Log out", 420);
            image("Project2.jpg", 380);
        }

        setVisible(true);
    }

    private void section(String text, int x, int y) {
        JLabel label = new JLabel(text);
        label.setBounds(x, y, 150, 30);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        add(label);
    }

    private void menuButton(String text, int y) {
        JButton button = new JButton(text);
        button.setBounds(90, y, 270, 30);
        button.addActionListener(this);
        add(button);
    }

    private void image(String file, int x) {
        JLabel image = new JLabel(icon(file));
        image.setBounds(x, 120, 300, 300);
        add(image);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String meter = account.meterNo();
        switch (ae.getActionCommand()) {
            case "Add New Customer" -> new NewCustomer();
            case "Customer Details" -> new CustomerDetails();
            case "Deposit Details" -> new DepositDetails();
            case "Calculate Bill" -> new CalculateBill();
            case "Add Admin" -> new Signup(account);
            case "Update Information" -> new UpdateInformation(meter);
            case "View Information" -> new ViewInformation(meter);
            case "Pay Bill" -> new PayBill(meter);
            case "Bill Details" -> new BillDetails(meter);
            case "Generate Bill" -> new GenerateBill(meter);
            case "Notepad" -> launch(Tool.NOTEPAD);
            case "Calculator" -> launch(Tool.CALCULATOR);
            case "Log out" -> {
                closeAllWindows();
                new Login();
            }
            default -> throw new IllegalStateException("No action for " + ae.getActionCommand());
        }
    }

    private void launch(Tool tool) {
        try {
            SystemTools.launch(tool);
        } catch (IOException e) {
            showWarning("Couldn't open the " + tool.name().toLowerCase() + " on this computer.");
        }
    }

    private static void closeAllWindows() {
        for (Window window : Window.getWindows()) {
            window.dispose();
        }
    }
}
