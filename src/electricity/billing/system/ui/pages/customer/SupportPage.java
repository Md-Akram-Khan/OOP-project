package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.model.Complaint;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.Field;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Toast;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/** Report a problem (wrong bill, meter fault, outage) and follow its progress. Also lists the utility hotlines. */
public class SupportPage extends Page {

    /** Distributor hotlines in Bangladesh. */
    private static final String[][] HOTLINES = {
        {"DPDC", "16116", "Dhaka Power Distribution Company"},
        {"DESCO", "16120", "Dhaka Electric Supply Company"},
        {"BPDB", "16200", "Bangladesh Power Development Board"},
    };

    private final JComboBox<Complaint.Category> category = Field.combo();
    private final Field subject = Field.text("Subject", "e.g. My September bill looks too high", Glyph.PEN);
    private final JTextArea details = new JTextArea(5, 20);
    private final Btn send = new Btn("Send complaint", Glyph.ARROW_RIGHT, Btn.Style.PRIMARY).trailingIcon();
    private final JPanel list = Ui.vbox(12);

    public SupportPage(AppContext app) {
        super(app);
        add(header("Help & complaints", "Tell us what's wrong. You'll get a ticket number and updates in Notifications."),
                BorderLayout.NORTH);

        for (Complaint.Category c : Complaint.Category.values()) {
            category.addItem(c);
        }
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setFont(Theme.regular(14));
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setPreferredSize(new Dimension(300, 130));
        send.addActionListener(e -> sendComplaint());

        Card form = new Card(new BorderLayout()).pad(24, 26, 24, 26);
        form.add(Ui.vbox(14, Txt.h2("New complaint"), Field.of("What is it about?", category), subject,
                Txt.caps("Details"), Ui.left(detailScroll),
                Txt.small("Include dates, meter readings or anything else that helps.").wrap(460),
                Ui.hbox(0, Ui.glue(), send)), BorderLayout.NORTH);

        JPanel hotlines = Ui.vbox(12, Txt.h2("Emergency hotlines"),
                Txt.small("For outages, fire or a dangerous line, call your distributor directly.").wrap(320));
        for (String[] h : HOTLINES) {
            JPanel row = Ui.clear(new BorderLayout(12, 0));
            row.add(new IconTile(Glyph.PHONE, 38, Theme.DANGER), BorderLayout.WEST);
            row.add(Ui.vbox(2, Txt.h3(h[0] + "  ·  " + h[1]), Txt.small(h[2])), BorderLayout.CENTER);
            hotlines.add(javax.swing.Box.createVerticalStrut(10));
            hotlines.add(Ui.left(row));
        }
        Card hotlineCard = new Card(new BorderLayout()).pad(24, 26, 24, 26);
        hotlineCard.add(hotlines, BorderLayout.NORTH);

        JPanel top = Ui.clear(new BorderLayout(16, 0));
        top.add(form, BorderLayout.CENTER);
        top.add(Ui.top(Ui.fixedWidth(hotlineCard, 380)), BorderLayout.EAST);

        Card history = new Card(new BorderLayout(0, 14)).pad(24, 26, 24, 26);
        history.add(Txt.h2("My complaints"), BorderLayout.NORTH);
        history.add(list, BorderLayout.CENTER);
        body(Ui.vbox(16, top, history));
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, app.support::complaints, this::render);
    }

    private void render(List<Complaint> complaints) {
        list.removeAll();
        if (complaints.isEmpty()) {
            list.add(Ui.left(Txt.muted("You haven't sent any complaints.")));
        }
        for (Complaint c : complaints) {
            JPanel row = Ui.clear(new BorderLayout(14, 0));
            JPanel text = Ui.vbox(4, Txt.h3(c.subject()),
                    Txt.small(c.ticket() + "  ·  " + c.category().label() + "  ·  sent " + Fmt.dateTime(c.createdAt())));
            if (c.adminReply() != null && !c.adminReply().isBlank()) {
                text.add(Ui.left(Txt.body("Reply: " + c.adminReply()).wrap(700)));
            }
            row.add(text, BorderLayout.CENTER);
            Badge badge = new Badge("", Theme.BLUE);
            badge.set(Badge.Label.of(c.status()));
            row.add(Ui.top(badge), BorderLayout.EAST);
            list.add(Ui.left(row));
        }
        list.revalidate();
        list.repaint();
    }

    private void sendComplaint() {
        Complaint.Category c = (Complaint.Category) category.getSelectedItem();
        String s = subject.value();
        String d = details.getText().trim();
        send.setBusy(true, "Sending…");
        Tasks.run(() -> app.support.openComplaint(c, s, d), saved -> {
            send.setBusy(false, null);
            Toast.success(this, "Sent. Your ticket number is " + saved.ticket() + ".");
            subject.setValue("");
            details.setText("");
            onShow(null);
            app.refreshCounts();
        }, error -> {
            send.setBusy(false, null);
            Toast.error(this, Tasks.message(error));
        });
    }
}
