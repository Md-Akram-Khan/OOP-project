package electricity.billing.system.ui;

import electricity.billing.system.client.ApiClient;
import electricity.billing.system.client.AuthClient;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import electricity.billing.system.ui.components.Anim;
import electricity.billing.system.ui.components.AuroraBackground;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.FadeHost;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.GradientText;
import electricity.billing.system.ui.components.IconButton;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Segmented;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import electricity.billing.system.ui.components.CenterLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/** Sign in and account creation, beside an animated brand panel. */
public class AuthWindow extends JFrame {

    private static final int FORM_WIDTH = 392;

    private final AuthClient auth = new AuthClient();
    private final Txt title = Txt.h1("Welcome back");
    private final Txt subtitle = Txt.muted("Sign in to manage electricity billing.");
    private final Segmented tabs = new Segmented("Sign in", "Create account");
    private final FadeHost formHost = new FadeHost();
    private JPanel column;

    // Sign in
    private final Segmented loginRole = new Segmented("Admin", "Customer");
    private final Field username = Field.text("Username", "Enter your username", Glyph.USER);
    private final Field password = Field.password("Password", "Enter your password");
    private final Txt loginError = errorText();
    private final Btn signIn = new Btn("Sign in", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon().fullWidth();
    private JPanel signInPanel;
    private JPanel signInTop;

    // Create account
    private static final String HAVE_METER = "I have a meter";
    private static final String NEW_METER = "I need a new meter";
    private final Segmented signupRole = new Segmented(HAVE_METER, NEW_METER);
    private final Btn startNewMeter = new Btn("Start: verify phone and NID", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon().fullWidth();
    private final Field meter = Field.text("Meter number", "From your welcome letter", Glyph.HASH);
    private final Field email = Field.text("Registered email", "you@example.com", Glyph.MAIL);
    private final Field fullName = Field.text("Full name", "Your name", Glyph.USER);
    private final Field newUsername = Field.text("Username", "3-30 letters or numbers", Glyph.USER);
    private final Field newPassword = Field.password("Password", "At least 8 characters");
    private final Field confirm = Field.password("Confirm", "Repeat password");
    private final Txt signupError = errorText();
    private final Btn create = new Btn("Create account", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon().fullWidth();
    private final JPanel signupFields = Ui.clear(new BorderLayout());
    private JPanel signUpPanel;
    private JPanel signUpTop;

    public AuthWindow() {
        super("Electricity Billing System");
        setIconImages(Icons.appImages());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.add(brandPanel(), BorderLayout.CENTER);
        JPanel form = formSide();
        form.setPreferredSize(new Dimension(540, 10));
        root.add(form, BorderLayout.EAST);
        setContentPane(root);

        tabs.onChange(i -> showTab(i));
        signupRole.onChange(i -> buildSignupFields());
        signIn.addActionListener(e -> signIn());
        create.addActionListener(e -> createAccount());
        startNewMeter.addActionListener(e -> new NewMeterSignupDialog(this, user -> {
            Toast.success(this, "Account created. Sign in as a customer to apply for your meter.");
            username.setValue(user);
            password.setValue("");
            loginRole.select(1);
            tabs.select(0);
            password.input().requestFocusInWindow();
        }).setVisible(true));
        buildSignupFields();
        int formHeight = Math.max(signInPanel.getPreferredSize().height, signUpPanel.getPreferredSize().height + 70);
        formHost.setPreferredSize(new Dimension(FORM_WIDTH, formHeight));
        formHost.show(signInTop);
        getRootPane().setDefaultButton(signIn);

        setSize(1200, 780);
        setMinimumSize(new Dimension(1040, 700));
        setLocationRelativeTo(null);

        Tasks.run(auth::adminExists, exists -> {
            if (!exists) {
                signupRole.setOptions(List.of(HAVE_METER, NEW_METER, "Admin"));
            }
        }, error -> { });
    }

    // ---- Left: brand artwork ----------------------------------------------

    private JComponent brandPanel() {
        AuroraBackground art = new AuroraBackground(new BorderLayout());
        art.setBorder(new EmptyBorder(40, 52, 40, 52));

        Txt name = new Txt("Electricity", Theme.semibold(18), () -> Color.WHITE);
        Txt sub = new Txt("BILLING SYSTEM", Theme.semibold(10).deriveFont(Map.of(TextAttribute.TRACKING, 0.18)), () -> Theme.AQUA);
        art.add(Ui.hbox(12, IconTile.brand(Glyph.BOLT, 44), Ui.vbox(2, name, sub)), BorderLayout.NORTH);

        Txt line1 = new Txt("Power your billing,", Theme.semibold(46), () -> Color.WHITE);
        GradientText line2 = new GradientText("beautifully.", Theme.semibold(46), Theme.AQUA, Theme.AMBER);
        Txt pitch = new Txt("Customers, meters, bills and payments in one calm, secure workspace.",
                Theme.regular(17), () -> new Color(255, 255, 255, 185)).wrap(430);
        JPanel features = Ui.vbox(14,
                feature("Live bill previews with smart tariffs"),
                feature("Secure sessions and role-based access"),
                feature("Payments confirmed in a single click"));
        JPanel copy = Ui.vbox(0, line1, line2, gap(18), pitch);
        JPanel center = Ui.clear(new CenterLayout());
        center.add(Ui.vbox(34, copy, features));
        art.add(center, BorderLayout.CENTER);

        art.add(Ui.hbox(0, new ServerStatus(), Ui.glue()), BorderLayout.SOUTH);
        return art;
    }

    private static JComponent feature(String text) {
        Txt label = new Txt(text, Theme.regular(15), () -> new Color(255, 255, 255, 215));
        JComponent check = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(28, 28);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(Theme.AQUA, 45));
                g2.fill(new Ellipse2D.Double(0, 0, 28, 28));
                Icons.paint(g2, Glyph.CHECK, 7, 7, 14, Theme.AQUA, 2.6f);
                g2.dispose();
            }
        };
        return Ui.hbox(12, check, label);
    }

    // ---- Right: forms -----------------------------------------------------

    private JPanel formSide() {
        JPanel side = Ui.themed(new BorderLayout(), Theme::surface);
        IconButton theme = new IconButton(Theme.isDark() ? Glyph.SUN : Glyph.MOON, "Switch light / dark mode", false);
        theme.addActionListener(e -> {
            Theme.toggle();
            theme.setGlyph(Theme.isDark() ? Glyph.SUN : Glyph.MOON);
        });
        side.add(Ui.pad(Ui.hbox(0, Ui.glue(), theme), 18, 18, 0, 18), BorderLayout.NORTH);

        signInPanel = Ui.vbox(16,
                Txt.caps("Sign in as"), loginRole, username, password, loginError,
                signIn, link("New here?", "Create an account", () -> tabs.select(1)));
        signUpPanel = Ui.vbox(16,
                Txt.caps("I want to sign up as"), signupRole, signupFields, signupError,
                create, link("Already have an account?", "Sign in", () -> tabs.select(0)));
        signInTop = Ui.top(signInPanel);
        signUpTop = Ui.top(signUpPanel);

        column = Ui.vbox(0, title, gap(8), subtitle, gap(24), tabs, gap(22), formHost);
        column.setBorder(new EmptyBorder(0, 12, 0, 12));
        JPanel fixedWidth = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(FORM_WIDTH + 24, column.getPreferredSize().height);
            }
        };
        fixedWidth.setOpaque(false);
        fixedWidth.add(column, BorderLayout.CENTER);
        side.add(Ui.centered(fixedWidth), BorderLayout.CENTER);
        return side;
    }

    private static JComponent gap(int px) {
        JPanel gap = Ui.clear(null);
        gap.setPreferredSize(new Dimension(1, px));
        gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, px));
        return gap;
    }

    private static Txt errorText() {
        Txt error = new Txt(" ", Theme.regular(13), () -> Theme.DANGER).wrap(FORM_WIDTH - 10);
        error.setVisible(false);
        return error;
    }

    private static JComponent link(String lead, String action, Runnable onClick) {
        Txt link = new Txt(action, Theme.semibold(14), () -> Theme.BLUE);
        Ui.hand(link);
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }
        });
        return Ui.hbox(6, Txt.muted(lead), link);
    }

    private void showTab(int index) {
        boolean signInTab = index == 0;
        title.setText(signInTab ? "Welcome back" : "Create your account");
        subtitle.setText(signInTab ? "Sign in to manage electricity billing." : "Set up access in less than a minute.");
        formHost.show(signInTab ? signInTop : signUpTop);
        getRootPane().setDefaultButton(signInTab ? signIn : create);
    }

    private void buildSignupFields() {
        signupFields.removeAll();
        boolean customer = HAVE_METER.equals(signupRole.selectedOption());
        boolean newMeter = NEW_METER.equals(signupRole.selectedOption());
        JPanel passwords = Ui.columns(12, newPassword, confirm);
        JPanel fields = newMeter
                ? Ui.vbox(12,
                        Txt.body("No meter yet? Apply for one in three quick steps:"),
                        Txt.small("1.  Verify your mobile number with a one-time code").wrap(FORM_WIDTH - 10),
                        Txt.small("2.  Upload a photo of your NID card to verify your identity").wrap(FORM_WIDTH - 10),
                        Txt.small("3.  Choose a username and password, then apply for a meter").wrap(FORM_WIDTH - 10),
                        startNewMeter)
                : customer
                ? Ui.vbox(14, Ui.columns(12, meter, email), newUsername, passwords)
                : Ui.vbox(14, fullName, newUsername, passwords);
        create.setVisible(!newMeter);
        signupFields.add(fields, BorderLayout.CENTER);
        signupError.setVisible(false);
        signupFields.revalidate();
        signupFields.repaint();
    }

    // ---- Actions ----------------------------------------------------------

    private void signIn() {
        username.setError(null);
        password.setError(null);
        loginError.setVisible(false);
        String user = username.value();
        char[] pass = password.password();
        boolean missing = false;
        if (user.isEmpty()) {
            username.setError("Enter your username.");
            missing = true;
        }
        if (pass.length == 0) {
            password.setError("Enter your password.");
            missing = true;
        }
        if (missing) {
            shake();
            return;
        }
        Role role = loginRole.selectedIndex() == 0 ? Role.ADMIN : Role.CUSTOMER;
        signIn.setBusy(true, "Signing in…");
        Tasks.run(() -> auth.login(user, pass, role), this::openApp, error -> {
            signIn.setBusy(false, null);
            password.setValue("");
            loginError.setText(Tasks.message(error));
            loginError.setVisible(true);
            column.revalidate();
            shake();
        });
    }

    private void openApp(UserAccount account) {
        AppWindow window = new AppWindow(account);
        window.setVisible(true);
        dispose();
    }

    private void createAccount() {
        signupError.setVisible(false);
        newPassword.setError(null);
        confirm.setError(null);
        boolean customer = HAVE_METER.equals(signupRole.selectedOption());
        char[] pass = newPassword.password();
        if (!Arrays.equals(pass, confirm.password())) {
            confirm.setError("Passwords don't match.");
            shake();
            return;
        }
        String user = newUsername.value();
        String meterNo = meter.value();
        String mail = email.value();
        String name = fullName.value();
        create.setBusy(true, "Creating account…");
        Tasks.run(() -> {
            if (customer) {
                auth.registerCustomer(meterNo, mail, user, pass);
            } else {
                auth.createAdmin(user, name, pass);
            }
            return null;
        }, done -> {
            create.setBusy(false, null);
            Toast.success(this, "Account created. You can sign in now.");
            if (!customer) {
                signupRole.setOptions(List.of(HAVE_METER, NEW_METER));
                buildSignupFields();
            }
            username.setValue(user);
            password.setValue("");
            loginRole.select(customer ? 1 : 0);
            tabs.select(0);
            password.input().requestFocusInWindow();
        }, error -> {
            create.setBusy(false, null);
            signupError.setText(Tasks.message(error));
            signupError.setVisible(true);
            column.revalidate();
            shake();
        });
    }

    /** A quick side-to-side shake of the form, the classic "that didn't work" cue. */
    private void shake() {
        Anim.run(460, t -> {
            int dx = (int) Math.round(Math.sin(t * Math.PI * 7) * (1 - t) * 12);
            column.setBorder(new EmptyBorder(0, 12 + dx, 0, 12 - dx));
            column.revalidate();
        });
    }

    /** A glassy pill on the brand panel showing whether the server is reachable. */
    private static final class ServerStatus extends JPanel {
        private final long start = System.nanoTime();
        private final Timer pulse = new Timer(40, e -> repaint());
        private boolean online = true;

        ServerStatus() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(12, 40, 12, 18));
            Txt text = new Txt("Connected to " + ApiClient.get().baseUrl(), Theme.regular(13), () -> new Color(255, 255, 255, 200));
            add(text, BorderLayout.CENTER);
            Tasks.run(() -> ApiClient.get().isServerUp(), up -> {
                online = up;
                text.setText(up ? "Connected to " + ApiClient.get().baseUrl() : "Server offline: " + ApiClient.get().baseUrl());
            }, error -> { });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            pulse.start();
        }

        @Override
        public void removeNotify() {
            pulse.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(new Color(255, 255, 255, 18));
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
            g2.setColor(new Color(255, 255, 255, 30));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, h, h));
            Color dot = online ? Theme.SUCCESS : Theme.DANGER;
            double phase = ((System.nanoTime() - start) / 1e9) % 1.6 / 1.6;
            double ring = 5 + phase * 9;
            g2.setColor(Theme.alpha(dot, (int) (120 * (1 - phase))));
            g2.fill(new Ellipse2D.Double(22 - ring, h / 2.0 - ring, ring * 2, ring * 2));
            g2.setColor(dot);
            g2.fill(new Ellipse2D.Double(17, h / 2.0 - 5, 10, 10));
            g2.dispose();
        }
    }
}
