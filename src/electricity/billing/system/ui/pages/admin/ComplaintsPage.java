package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.model.Complaint;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.CenterLayout;
import electricity.billing.system.ui.components.DataTable;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.Segmented;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Every customer complaint, newest first. Select one to reply and change its status. */
public class ComplaintsPage extends Page {

    private final DataTable table = new DataTable("Ticket", "Customer", "Subject", "Status");
    private final Segmented filter = new Segmented("All", "Open", "In progress", "Resolved");
    private final Txt summary = Txt.muted(" ");
    private final CardLayout states = new CardLayout();
    private final JPanel content = Ui.clear(states);

    private final Txt detailTicket = Txt.caps(" ");
    private final Txt detailSubject = Txt.h2(" ").wrap(290);
    private final Txt detailMeta = Txt.small(" ").wrap(290);
    private final Txt detailText = Txt.body(" ").wrap(290);
    private final JComboBox<Complaint.Status> status = Field.combo();
    private final JTextArea reply = new JTextArea(5, 20);
    private final Btn save = new Btn("Save and notify customer", Glyph.CHECK, Btn.Style.PRIMARY).fullWidth();
    private final JPanel detail;
    private final JPanel noSelection;
    private final CardLayout sideStates = new CardLayout();
    private final JPanel side = Ui.clear(sideStates);

    private List<Complaint> all = List.of();
    private List<Complaint> shown = new ArrayList<>();
    private Complaint selected;

    public ComplaintsPage(AppContext app) {
        super(app);
        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Complaints"), summary), BorderLayout.WEST);
        JPanel right = Ui.clear(new CenterLayout());
        right.add(Ui.width(filter, 420));
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        filter.onChange(i -> applyFilter());

        table.column(0, new DataTable.TextRenderer(javax.swing.SwingConstants.LEFT, true), 110);
        table.column(1, new DataTable.NameRenderer(), 200);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.column(3, new DataTable.StatusRenderer(), 140);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                select();
            }
        });

        for (Complaint.Status s : Complaint.Status.values()) {
            status.addItem(s);
        }
        reply.setLineWrap(true);
        reply.setWrapStyleWord(true);
        reply.setFont(Theme.regular(14));
        reply.putClientProperty("JTextField.placeholderText", "Reply to the customer…");
        JScrollPane replyScroll = new JScrollPane(reply);
        replyScroll.setPreferredSize(new Dimension(300, 120));
        save.addActionListener(e -> saveReply());

        detail = Ui.vbox(10, detailTicket, detailSubject, detailMeta, Ui.pad(detailText, 6, 0, 6, 0),
                Field.of("Status", status), Txt.caps("Reply"), Ui.left(replyScroll), save);
        noSelection = EmptyState.of(Glyph.MAIL, "Choose a complaint", "Select a complaint to read it and reply.");
        side.add(Ui.top(detail), "detail");
        side.add(noSelection, "none");

        Card tableCard = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        tableCard.add(table.scroll(), BorderLayout.CENTER);
        content.add(tableCard, "table");
        content.add(EmptyState.of(Glyph.CHECK_CIRCLE, "No complaints here", "Complaints customers send appear here."), "empty");

        Card sideCard = new Card(new BorderLayout()).pad(22, 22, 22, 22);
        sideCard.add(side, BorderLayout.CENTER);

        JPanel row = Ui.clear(new BorderLayout(16, 0));
        row.add(content, BorderLayout.CENTER);
        row.add(Ui.fixedWidth(sideCard, 340), BorderLayout.EAST);
        body(row);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.support::complaints, list -> {
            all = list;
            applyFilter();
            app.refreshCounts();
        });
    }

    private void applyFilter() {
        Complaint.Status wanted = switch (filter.selectedIndex()) {
            case 1 -> Complaint.Status.OPEN;
            case 2 -> Complaint.Status.IN_PROGRESS;
            case 3 -> Complaint.Status.RESOLVED;
            default -> null;
        };
        long keep = selected == null ? -1 : selected.id();
        shown = all.stream().filter(c -> wanted == null || c.status() == wanted).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        table.clear();
        for (Complaint c : shown) {
            table.addRow(c.ticket(), c.customerName(), c.subject(), Badge.Label.of(c.status()));
        }
        long open = all.stream().filter(c -> c.status() == Complaint.Status.OPEN).count();
        long progress = all.stream().filter(c -> c.status() == Complaint.Status.IN_PROGRESS).count();
        summary.setText(all.size() + (all.size() == 1 ? " complaint" : " complaints") + "  ·  " + open + " open  ·  "
                + progress + " in progress");
        states.show(content, shown.isEmpty() ? "empty" : "table");
        selected = null;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).id() == keep) {
                int view = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(view, view);
            }
        }
        select();
    }

    private void select() {
        int row = table.getSelectedRow();
        selected = row < 0 ? null : shown.get(table.convertRowIndexToModel(row));
        if (selected == null) {
            sideStates.show(side, "none");
            return;
        }
        detailTicket.setText(selected.ticket() + "  ·  " + selected.category().label().toUpperCase());
        detailSubject.setText(selected.subject());
        detailMeta.setText(selected.customerName() + "  ·  Meter #" + selected.meterNo() + "  ·  " + Fmt.dateTime(selected.createdAt()));
        detailText.setText(selected.details());
        status.setSelectedItem(selected.status());
        reply.setText(selected.adminReply() == null ? "" : selected.adminReply());
        sideStates.show(side, "detail");
        side.revalidate();
    }

    private void saveReply() {
        Complaint c = selected;
        if (c == null) {
            return;
        }
        Complaint.Status s = (Complaint.Status) status.getSelectedItem();
        String text = reply.getText().trim();
        save.setBusy(true, "Saving…");
        Tasks.run(() -> app.support.updateComplaint(c.id(), s, text), saved -> {
            save.setBusy(false, null);
            Toast.success(this, saved.ticket() + " saved. " + saved.customerName() + " has been notified.");
            onShow(null);
        }, error -> {
            save.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
