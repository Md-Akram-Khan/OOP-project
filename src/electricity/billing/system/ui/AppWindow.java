package electricity.billing.system.ui;

import electricity.billing.system.model.UserAccount;
import electricity.billing.system.ui.components.FadeHost;
import electricity.billing.system.ui.components.Sidebar;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.pages.admin.AddCustomerPage;
import electricity.billing.system.ui.pages.admin.BillsPage;
import electricity.billing.system.ui.pages.admin.CalculateBillPage;
import electricity.billing.system.ui.pages.admin.CustomersPage;
import electricity.billing.system.ui.pages.admin.DashboardPage;
import electricity.billing.system.ui.pages.admin.TeamPage;
import electricity.billing.system.ui.pages.customer.HomePage;
import electricity.billing.system.ui.pages.customer.MyBillsPage;
import electricity.billing.system.ui.pages.customer.PayPage;
import electricity.billing.system.ui.pages.customer.ProfilePage;
import electricity.billing.system.ui.pages.customer.StatementPage;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JPanel;

/** The main window after login: sidebar on the left, the current page on the right. */
public class AppWindow extends JFrame {

    private final AppContext app;
    private final Sidebar sidebar;
    private final FadeHost host = new FadeHost();
    private final Map<String, Page> pages = new HashMap<>();
    private final Map<String, Component> views = new HashMap<>();

    public AppWindow(UserAccount account) {
        super("Electricity Billing System");
        app = new AppContext(account, this);
        setIconImages(Icons.appImages());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                app.auth.logout();
                dispose();
            }
        });

        sidebar = new Sidebar(account, key -> navigate(key, null), Theme::toggle, this::logout);
        String first;
        if (account.isAdmin()) {
            sidebar.section("Overview");
            add("dashboard", "Dashboard", Glyph.DASHBOARD, new DashboardPage(app));
            sidebar.section("Manage");
            add("customers", "Customers", Glyph.USERS, new CustomersPage(app));
            add("add-customer", "New customer", Glyph.USER_PLUS, new AddCustomerPage(app));
            add("calculate", "Calculate bill", Glyph.CALCULATOR, new CalculateBillPage(app));
            add("bills", "Bills & payments", Glyph.RECEIPT, new BillsPage(app));
            add("team", "Team", Glyph.SHIELD, new TeamPage(app));
            first = "dashboard";
        } else {
            sidebar.section("Overview");
            add("home", "Home", Glyph.HOME, new HomePage(app));
            sidebar.section("Billing");
            add("bills", "My bills", Glyph.RECEIPT, new MyBillsPage(app));
            add("statement", "Invoice", Glyph.FILE_TEXT, new StatementPage(app));
            add("pay", "Pay bill", Glyph.CREDIT_CARD, new PayPage(app));
            sidebar.section("Account");
            add("profile", "Profile", Glyph.USER, new ProfilePage(app));
            first = "home";
        }

        JPanel root = Ui.themed(new BorderLayout(), Theme::bg);
        sidebar.setPreferredSize(new Dimension(Sidebar.WIDTH, 10));
        root.add(sidebar, BorderLayout.WEST);
        root.add(host, BorderLayout.CENTER);
        setContentPane(root);

        setSize(1320, 860);
        setMinimumSize(new Dimension(1120, 720));
        setLocationRelativeTo(null);
        navigate(first, null);
    }

    private void add(String key, String label, Glyph glyph, Page page) {
        sidebar.item(key, label, glyph);
        pages.put(key, page);
        views.put(key, page.scrolls() ? Ui.scroll(page) : page);
    }

    public void navigate(String key, Object param) {
        Page page = pages.get(key);
        if (page == null) {
            return;
        }
        sidebar.select(key);
        host.show(views.get(key));
        page.onShow(param);
    }

    private void logout() {
        app.auth.logout();
        dispose();
        new AuthWindow().setVisible(true);
    }
}
