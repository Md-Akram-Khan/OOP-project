package electricity.billing.system.ui.pages.admin;

import com.formdev.flatlaf.FlatClientProperties;
import electricity.billing.system.common.Api;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.CenterLayout;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/** Every administrator, searchable like the customer list, and a form to add another. */
public class TeamPage extends Page {

    private final DataTable table = new DataTable("Name", "Username", "Role", "Last sign-in");
    private final JTextField search = new JTextField();
    private final Txt count = Txt.muted("Everyone with admin access.");

    private final Field name = Field.text("Full name", "Their name", Glyph.USER);
    private final Field username = Field.text("Username", "3-30 letters or numbers", Glyph.USER);
    private final Field password = Field.password("Password", "At least 8 characters");
    private final Field confirm = Field.password("Confirm password", "Repeat password");
    private final Btn addBtn = new Btn("Add administrator", Glyph.CHECK, Btn.Style.PRIMARY);
    private final Btn toggle = new Btn("Add administrator", Glyph.USER_PLUS, Btn.Style.PRIMARY);
    private final Card form = new Card(new BorderLayout()).pad(24, 26, 24, 26);

    public TeamPage(AppContext app) {
        super(app);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search name or username…");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.of(Glyph.SEARCH, 17, Theme::muted));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.MINIMUM_HEIGHT, Field.INPUT_HEIGHT - 2);
        search.setPreferredSize(new Dimension(260, Field.INPUT_HEIGHT - 2));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        toggle.addActionListener(e -> showForm(!form.isVisible()));
        addBtn.addActionListener(e -> addAdmin());
        Btn cancel = new Btn("Cancel", Btn.Style.GHOST);
        cancel.addActionListener(e -> showForm(false));

        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Team"), count), BorderLayout.WEST);
        JPanel actions = Ui.clear(new CenterLayout());
        actions.add(Ui.hbox(10, search, toggle));
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        form.add(Ui.vbox(16,
                Ui.vbox(4, Txt.h2("New administrator"),
                        Txt.small("They can sign in straight away and do everything you can. Share the password privately.")),
                Ui.columns(16, name, username, password, confirm),
                Ui.hbox(10, Ui.glue(), cancel, addBtn)), BorderLayout.NORTH);
        form.setVisible(false);

        table.column(0, new DataTable.NameRenderer(), 260);
        table.column(1, new DataTable.TextRenderer(javax.swing.SwingConstants.LEFT, true), 180);
        table.column(2, new DataTable.StatusRenderer(), 170);
        table.column(3, new DataTable.MutedRenderer(), 220);
        Card card = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        card.add(table.scroll(), BorderLayout.CENTER);

        JPanel content = Ui.clear(new BorderLayout(0, 16));
        content.add(form, BorderLayout.NORTH);
        content.add(card, BorderLayout.CENTER);
        body(content);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.auth::admins, this::render);
    }

    private void render(List<Api.AdminInfo> admins) {
        table.clear();
        for (Api.AdminInfo a : admins) {
            String shown = a.name() == null || a.name().isBlank() ? a.username() : a.name();
            table.addRow(shown, a.username() + (a.you() ? "  ·  you" : ""), new Badge.Label("Administrator", Theme.VIOLET),
                    a.lastSignIn() == null ? "Not recorded yet" : Fmt.dateTime(a.lastSignIn()));
        }
        count.setText(admins.size() + (admins.size() == 1 ? " administrator" : " administrators") + " with full access.");
        filter();
    }

    @SuppressWarnings("unchecked")
    private void filter() {
        TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) table.getRowSorter();
        String text = search.getText().trim();
        sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private void showForm(boolean show) {
        form.setVisible(show);
        toggle.setVisible(!show);
        if (!show) {
            for (Field f : new Field[] {name, username, password, confirm}) {
                f.setValue("");
                f.setError(null);
            }
        }
        revalidate();
        repaint();
    }

    private void addAdmin() {
        confirm.setError(null);
        char[] pass = password.password();
        if (!Arrays.equals(pass, confirm.password())) {
            confirm.setError("Passwords don't match.");
            return;
        }
        String user = username.value();
        String person = name.value();
        addBtn.setBusy(true, "Adding…");
        Tasks.run(() -> {
            app.auth.createAdmin(user, person, pass);
            return null;
        }, done -> {
            addBtn.setBusy(false, null);
            Toast.success(this, person + " is now an administrator.");
            showForm(false);
            onShow(null);
        }, error -> {
            addBtn.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
