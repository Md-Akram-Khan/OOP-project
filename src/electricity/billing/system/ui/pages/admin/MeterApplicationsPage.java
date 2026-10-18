package electricity.billing.system.ui.pages.admin;

import electricity.billing.system.model.Meter;
import electricity.billing.system.model.MeterApplication;
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
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * Applications for a new meter from phone- and NID-verified applicants. Review
 * them, move them along with a note to the applicant, and once the meter is
 * installed, connect it: that creates the customer and turns the applicant's
 * account into their customer account.
 */
public class MeterApplicationsPage extends Page {

    private final DataTable table = new DataTable("Ref", "Applicant", "District", "Category", "Status");
    private final Segmented filter = new Segmented("All", "New", "In review", "Approved", "Done");
    private final Txt summary = Txt.muted(" ");
    private final CardLayout states = new CardLayout();
    private final JPanel content = Ui.clear(states);

    private final Txt detailRef = Txt.caps(" ");
    private final Txt detailName = Txt.h2(" ").wrap(270);
    private final Txt detailInfo = Txt.small(" ").wrap(270);
    private final Txt detailAddress = Txt.body(" ").wrap(270);
    private final Txt detailNotes = Txt.small(" ").wrap(270);
    private final JComboBox<MeterApplication.Status> status = Field.combo();
    private final JTextArea note = new JTextArea(3, 20);
    private final Btn save = new Btn("Save and update applicant", Glyph.CHECK, Btn.Style.PRIMARY).fullWidth();
    private final Field meterNo = Field.text("Meter number on the installed meter", "Exactly as printed", Glyph.HASH);
    private final JComboBox<String> meterType = Field.combo();
    private final Btn connect = new Btn("Connect meter", Glyph.BOLT, Btn.Style.SUCCESS).fullWidth();
    private final JPanel reviewBox;
    private final JPanel connectBox;
    private final Txt connectedText = Txt.h3(" ").wrap(270);
    private final CardLayout sideStates = new CardLayout();
    private final JPanel side = Ui.clear(sideStates);

    private List<MeterApplication> all = List.of();
    private List<MeterApplication> shown = new ArrayList<>();
    private MeterApplication selected;

    public MeterApplicationsPage(AppContext app) {
        super(app);
        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Meter applications"), summary), BorderLayout.WEST);
        JPanel right = Ui.clear(new CenterLayout());
        right.add(Ui.width(filter, 470));
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        filter.onChange(i -> applyFilter());

        table.column(0, new DataTable.TextRenderer(javax.swing.SwingConstants.LEFT, true), 115);
        table.column(1, new DataTable.NameRenderer(), 200);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.column(4, new DataTable.StatusRenderer(), 160);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                select();
            }
        });

        for (MeterApplication.Status s : MeterApplication.Status.values()) {
            if (s != MeterApplication.Status.CONNECTED) {
                status.addItem(s);
            }
        }
        Meter.TYPES.forEach(meterType::addItem);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setFont(Theme.regular(14));
        JScrollPane noteScroll = new JScrollPane(note);
        noteScroll.setPreferredSize(new Dimension(280, 80));
        save.addActionListener(e -> saveStatus());
        connect.addActionListener(e -> connectMeter());

        reviewBox = Ui.vbox(10, Field.of("Status", status), Txt.caps("Note to the applicant"), Ui.left(noteScroll), save);
        connectBox = Ui.vbox(10, Txt.h3("Meter installed?"),
                Txt.small("Enter the number printed on the new meter. This creates the customer, sets up the meter, and turns "
                        + "the applicant's sign-in into their customer account.").wrap(270),
                meterNo, Field.of("Meter type", meterType), connect);
        JPanel detail = Ui.vbox(10, detailRef, detailName, detailInfo, detailAddress, detailNotes, connectedText, reviewBox, connectBox);
        side.add(Ui.top(detail), "detail");
        side.add(EmptyState.of(Glyph.FILE_TEXT, "Choose an application", "Select one to review it."), "none");

        Card tableCard = new Card(new BorderLayout()).pad(6, 6, 6, 6);
        tableCard.add(table.scroll(), BorderLayout.CENTER);
        content.add(tableCard, "table");
        content.add(EmptyState.of(Glyph.BOLT, "No applications here",
                "Applications from people who need a new meter appear here."), "empty");

        Card sideCard = new Card(new BorderLayout()).pad(22, 22, 22, 22);
        sideCard.add(Ui.scroll(side), BorderLayout.CENTER);
        JPanel row = Ui.clear(new BorderLayout(16, 0));
        row.add(content, BorderLayout.CENTER);
        row.add(Ui.fixedWidth(sideCard, 360), BorderLayout.EAST);
        body(row);
    }

    @Override
    public boolean scrolls() {
        return false;
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.applications::list, list -> {
            all = list;
            applyFilter();
            app.refreshCounts();
        });
    }

    private void applyFilter() {
        int f = filter.selectedIndex();
        long keep = selected == null ? -1 : selected.id();
        shown = all.stream().filter(a -> switch (f) {
            case 1 -> a.status() == MeterApplication.Status.SUBMITTED;
            case 2 -> a.status() == MeterApplication.Status.UNDER_REVIEW;
            case 3 -> a.status() == MeterApplication.Status.APPROVED;
            case 4 -> a.status() == MeterApplication.Status.CONNECTED || a.status() == MeterApplication.Status.REJECTED;
            default -> true;
        }).collect(Collectors.toCollection(ArrayList::new));
        table.clear();
        for (MeterApplication a : shown) {
            table.addRow(a.reference(), a.applicantName(), a.district(), a.billType().displayName() + " · " + Fmt.kw(a.loadKw()),
                    Badge.Label.of(a.status()));
        }
        long fresh = all.stream().filter(a -> a.status() == MeterApplication.Status.SUBMITTED).count();
        long approved = all.stream().filter(a -> a.status() == MeterApplication.Status.APPROVED).count();
        summary.setText(all.size() + (all.size() == 1 ? " application" : " applications") + "  ·  " + fresh + " new  ·  "
                + approved + " approved, waiting for a meter");
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
        MeterApplication a = selected;
        detailRef.setText(a.reference() + "  ·  " + a.status().label().toUpperCase());
        detailName.setText(a.applicantName());
        detailInfo.setText("NID " + a.nidNo() + " (verified)  ·  " + a.phone() + " (verified)  ·  " + a.email()
                + "  ·  submitted " + Fmt.dateTime(a.createdAt()));
        detailAddress.setText(a.address() + ", " + a.district() + ", " + a.division() + "\n" + a.billType().displayName()
                + " · " + Fmt.kw(a.loadKw()) + " · " + a.phase() + " · " + a.ownership());
        detailNotes.setText(a.notes() == null ? " " : "Applicant's note: " + a.notes());
        boolean connected = a.status() == MeterApplication.Status.CONNECTED;
        connectedText.setText(connected ? "Meter " + a.meterNo() + " connected. The applicant is now a customer." : " ");
        connectedText.setVisible(connected);
        reviewBox.setVisible(!connected);
        connectBox.setVisible(a.status() == MeterApplication.Status.APPROVED);
        status.setSelectedItem(a.status());
        note.setText(a.adminNote() == null ? "" : a.adminNote());
        meterNo.setValue("");
        sideStates.show(side, "detail");
        side.revalidate();
    }

    private void saveStatus() {
        MeterApplication a = selected;
        if (a == null) {
            return;
        }
        MeterApplication.Status s = (MeterApplication.Status) status.getSelectedItem();
        String text = note.getText().trim();
        save.setBusy(true, "Saving…");
        Tasks.run(() -> app.applications.update(a.id(), s, text), saved -> {
            save.setBusy(false, null);
            Toast.success(this, saved.reference() + " is now " + saved.status().label().toLowerCase() + ".");
            onShow(null);
        }, error -> {
            save.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }

    private void connectMeter() {
        MeterApplication a = selected;
        if (a == null) {
            return;
        }
        String number = meterNo.value();
        String type = (String) meterType.getSelectedItem();
        connect.setBusy(true, "Connecting…");
        Tasks.run(() -> app.applications.connect(a.id(), number, type), saved -> {
            connect.setBusy(false, null);
            meterNo.setError(null);
            Toast.success(this, "Meter " + saved.meterNo() + " connected. " + saved.applicantName() + " is now a customer.");
            onShow(null);
        }, error -> {
            connect.setBusy(false, null);
            meterNo.setError(Tasks.message(error));
        });
    }
}
