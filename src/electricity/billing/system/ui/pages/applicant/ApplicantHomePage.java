package electricity.billing.system.ui.pages.applicant;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Badge;
import electricity.billing.system.ui.components.Btn;
import electricity.billing.system.ui.components.Callout;
import electricity.billing.system.ui.components.Card;
import electricity.billing.system.ui.components.EmptyState;
import electricity.billing.system.ui.components.IconTile;
import electricity.billing.system.ui.components.Tasks;
import electricity.billing.system.ui.components.Txt;
import electricity.billing.system.ui.components.Ui;
import electricity.billing.system.ui.theme.Fmt;
import electricity.billing.system.ui.theme.Icons.Glyph;
import electricity.billing.system.ui.theme.Theme;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

/** An applicant's home: their verified identity and where their meter application stands. */
public class ApplicantHomePage extends Page {

    private final Txt greeting = Txt.h1(" ");
    private final Txt identity = Txt.muted(" ");
    private final Btn apply = new Btn("Apply for a new meter", Glyph.PLUS, Btn.Style.PRIMARY);
    private final JPanel list = Ui.vbox(14);

    public ApplicantHomePage(AppContext app) {
        super(app);
        apply.addActionListener(e -> app.go("apply"));
        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, greeting, identity), BorderLayout.WEST);
        header.add(Ui.top(apply), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        body(list);
    }

    @Override
    public void onShow(Object param) {
        greeting.setText("Welcome, " + Fmt.firstName(app.account().name()));
        Tasks.run(this, () -> new Object[] {app.applications.profile(), app.applications.list()}, result -> {
            Api.ApplicantProfile me = (Api.ApplicantProfile) result[0];
            @SuppressWarnings("unchecked")
            List<MeterApplication> apps = (List<MeterApplication>) result[1];
            identity.setText("✓ Phone " + me.phone() + " verified  ·  ✓ NID " + me.nidNo() + " verified");
            render(apps);
        });
    }

    private void render(List<MeterApplication> apps) {
        list.removeAll();
        boolean open = apps.stream().anyMatch(a -> a.status().isOpen());
        apply.setEnabled(!open);
        apply.setToolTipText(open ? "You can apply again once your current application is decided." : null);
        if (apps.isEmpty()) {
            Btn first = new Btn("Apply for a new meter", Glyph.PLUS, Btn.Style.PRIMARY);
            first.addActionListener(e -> app.go("apply"));
            list.add(EmptyState.of(Glyph.BOLT, "No application yet",
                    "Your phone and NID are verified. Apply for a meter and we'll review it.", first));
        }
        for (MeterApplication a : apps) {
            list.add(card(a));
        }
        list.revalidate();
        list.repaint();
    }

    private Card card(MeterApplication a) {
        Card card = new Card(new BorderLayout(16, 0)).pad(20, 22, 20, 22);
        Badge badge = new Badge("", Theme.BLUE);
        badge.set(Badge.Label.of(a.status()));
        JPanel head = Ui.clear(new BorderLayout());
        head.add(Ui.hbox(10, Txt.h2("New meter application"), badge), BorderLayout.WEST);
        head.add(Txt.small(a.reference() + "  ·  submitted " + Fmt.dateTime(a.createdAt())), BorderLayout.EAST);
        JPanel body = Ui.vbox(6, head,
                Txt.body(a.address() + ", " + a.district() + ", " + a.division()).wrap(760),
                Txt.small(a.billType().displayName() + "  ·  " + Fmt.kw(a.loadKw()) + "  ·  " + a.phase() + "  ·  "
                        + a.ownership() + "  ·  " + a.email()));
        if (a.adminNote() != null && !a.adminNote().isBlank()) {
            body.add(Ui.left(new Callout(Glyph.MAIL, a.status() == MeterApplication.Status.REJECTED ? Theme.DANGER : Theme.BLUE,
                    "Note from the office", 720).lines(a.adminNote())));
        }
        if (a.status() == MeterApplication.Status.CONNECTED) {
            Btn signOut = new Btn("Sign out to switch to your customer account", Glyph.LOGOUT, Btn.Style.SUCCESS);
            signOut.addActionListener(e -> app.logout());
            body.add(Ui.left(Txt.h3("Your meter " + a.meterNo() + " is connected!")));
            body.add(Ui.left(signOut));
        }
        card.add(new IconTile(Glyph.BOLT, 44, a.status() == MeterApplication.Status.CONNECTED ? Theme.SUCCESS : Theme.BLUE),
                BorderLayout.WEST);
        card.add(body, BorderLayout.CENTER);
        return card;
    }
}
