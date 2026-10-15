package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Arrays;
import javax.swing.JPanel;

/** Lets an admin give someone else admin access. */
public class TeamPage extends Page {

    private final Field name = Field.text("Full name", "Their name", Glyph.USER);
    private final Field username = Field.text("Username", "3-30 letters or numbers", Glyph.USER);
    private final Field password = Field.password("Password", "At least 8 characters");
    private final Field confirm = Field.password("Confirm password", "Repeat password");
    private final Btn addBtn = new Btn("Add administrator", Glyph.SHIELD, Btn.Style.PRIMARY);

    public TeamPage(AppContext app) {
        super(app);
        add(header("Team", "Give a colleague admin access to the billing system."), BorderLayout.NORTH);
        addBtn.addActionListener(e -> addAdmin());

        Card form = new Card(new BorderLayout()).pad(26, 28, 26, 28);
        form.add(Ui.vbox(18,
                Ui.hbox(14, IconTile.brand(Glyph.SHIELD, 46),
                        Ui.vbox(4, Txt.h2("Add an administrator"), Txt.small("They can sign in straight away."))),
                Ui.columns(16, name, username),
                Ui.columns(16, password, confirm),
                Ui.hbox(0, Ui.glue(), addBtn)), BorderLayout.NORTH);

        Card info = new Card(new BorderLayout()).pad(24, 24, 24, 24);
        info.add(Ui.vbox(14,
                new IconTile(Glyph.INFO, 40, Theme.BLUE),
                Txt.h3("What admins can do"),
                Txt.muted("Add customers and meters, record monthly bills, and confirm payments. "
                        + "Admins also see every customer's details.").wrap(260),
                Txt.h3("Keep it safe"),
                Txt.muted("Share passwords privately, never by email or chat. "
                        + "After five wrong passwords, an account is locked for 15 minutes.").wrap(260)), BorderLayout.NORTH);

        JPanel row = Ui.clear(new BorderLayout(16, 0));
        row.add(form, BorderLayout.CENTER);
        row.add(Ui.fixedWidth(info, 340), BorderLayout.EAST);
        body(row);
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
            for (Field f : new Field[] {name, username, password, confirm}) {
                f.setValue("");
            }
        }, error -> {
            addBtn.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
