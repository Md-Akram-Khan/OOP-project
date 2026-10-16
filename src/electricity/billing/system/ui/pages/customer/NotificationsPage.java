package electricity.billing.system.ui.pages.customer;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Notification;
import electricity.billing.system.ui.AppContext;
import electricity.billing.system.ui.Page;
import electricity.billing.system.ui.components.Btn;
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
import java.awt.Color;
import java.time.LocalDateTime;
import javax.swing.JPanel;

/** New bills, payments, complaint replies, usage alerts and due-date reminders. Opening the page marks them read. */
public class NotificationsPage extends Page {

    private final JPanel list = Ui.vbox(10);
    private final Txt summary = Txt.muted(" ");

    public NotificationsPage(AppContext app) {
        super(app);
        Btn pay = new Btn("Pay bill", Glyph.CREDIT_CARD, Btn.Style.SECONDARY);
        pay.addActionListener(e -> app.go("pay"));
        JPanel header = Ui.clear(new BorderLayout());
        header.add(Ui.vbox(6, Txt.h1("Notifications"), summary), BorderLayout.WEST);
        header.add(Ui.top(pay), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        body(list);
    }

    @Override
    public void onShow(Object param) {
        Tasks.run(this, () -> {
            Api.NotificationFeed feed = app.support.notifications();
            app.support.markNotificationsRead();
            return feed;
        }, feed -> {
            render(feed);
            app.refreshCounts();
        });
    }

    private void render(Api.NotificationFeed feed) {
        list.removeAll();
        LocalDateTime now = LocalDateTime.now(Dates.ZONE);
        long reminders = feed.items().stream().filter(Notification::isReminder).count();
        summary.setText(feed.items().isEmpty() ? "You're all caught up."
                : feed.unread() + " new" + (reminders == 0 ? "" : "  ·  " + reminders + " payment reminder" + (reminders == 1 ? "" : "s")));
        if (feed.items().isEmpty()) {
            list.add(EmptyState.of(Glyph.CHECK_CIRCLE, "Nothing yet", "New bills, payments and replies will show up here."));
        }
        for (Notification n : feed.items()) {
            Card card = new Card(new BorderLayout(14, 0)).pad(16, 18, 16, 18);
            card.add(new IconTile(glyph(n.kind()), 40, color(n.kind())), BorderLayout.WEST);
            card.add(Ui.vbox(4, Txt.h3(n.title()), Txt.muted(n.body()).wrap(760)), BorderLayout.CENTER);
            String when = n.isReminder() ? "Until paid" : Fmt.ago(n.createdAt(), now);
            card.add(Ui.top(Txt.small(when + (n.read() || n.isReminder() ? "" : "  ·  new"))), BorderLayout.EAST);
            list.add(card);
        }
        list.revalidate();
        list.repaint();
    }

    private static Glyph glyph(Notification.Kind kind) {
        return switch (kind) {
            case BILL -> Glyph.RECEIPT;
            case PAYMENT -> Glyph.CHECK_CIRCLE;
            case COMPLAINT -> Glyph.MAIL;
            case USAGE -> Glyph.TRENDING_UP;
            case DUE_SOON -> Glyph.CLOCK;
            case OVERDUE -> Glyph.ALERT;
        };
    }

    private static Color color(Notification.Kind kind) {
        return switch (kind) {
            case BILL -> Theme.BLUE;
            case PAYMENT -> Theme.SUCCESS;
            case COMPLAINT -> Theme.VIOLET;
            case USAGE -> Theme.AMBER;
            case DUE_SOON -> Theme.WARNING;
            case OVERDUE -> Theme.DANGER;
        };
    }
}
