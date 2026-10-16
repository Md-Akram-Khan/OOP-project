package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.model.AuditEntry;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.Locale;
import javax.swing.JPanel;

/** Who changed what and when: bills, payments, customers, meters and complaints. Read-only. */
public class AuditLogPage extends Page {

    private static final int LIMIT = 300;

    private final DataTable table = new DataTable("When", "Who", "Action", "Record", "Details");
    private final Txt summary = Txt.muted(" ");
    private final CardLayout states = new CardLayout();
    private final JPanel content = Ui.clear(states);

    public AuditLogPage(AppContext app) {
        super(app);
        Btn refresh = new Btn("Refresh", Glyph.REFRESH, Btn.Style.SECONDARY);
        refresh.addActionListener(e -> onShow(null));
        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Activity log"), summary), BorderLayout.WEST);
        header.add(Ui.top(refresh), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        table.column(0, new DataTable.MutedRenderer(), 170);
        table.column(1, new DataTable.TextRenderer(javax.swing.SwingConstants.LEFT, true), 190);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.column(4, new DataTable.MutedRenderer(), 380);

        Card card = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        card.add(table.scroll(), BorderLayout.CENTER);
        content.add(card, "table");
        content.add(EmptyState.of(Glyph.SHIELD, "Nothing recorded yet",
                "Every change to bills, payments, customers and complaints is listed here."), "empty");
        body(content);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, () -> app.support.audit(LIMIT), list -> {
            table.clear();
            for (AuditEntry e : list) {
                table.addRow(Fmt.dateTime(e.at()), e.actor(), readable(e.action()), e.target() == null ? "" : e.target(),
                        e.details() == null ? "" : e.details());
            }
            summary.setText(list.isEmpty() ? "Every change is recorded here, with who made it."
                    : "The latest " + list.size() + " changes, newest first. Entries can't be edited or deleted.");
            states.show(content, list.isEmpty() ? "empty" : "table");
        });
    }

    /** BILL_CREATED → "Bill created". */
    private static String readable(String action) {
        String text = action.replace('_', ' ').toLowerCase(Locale.ROOT);
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
