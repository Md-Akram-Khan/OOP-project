package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.Dates;
import electricity.billing.system.common.Money;
import electricity.billing.system.db.BillDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.NotificationDao;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.Notification;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Customer notifications. Events (a new bill, a payment, a complaint update,
 * a usage alert) are stored when they happen. Due-date reminders are worked
 * out from the unpaid bills every time, so they can never go stale.
 */
public class NotificationService {

    /** Reminders start this many days before the due date. */
    static final int REMIND_DAYS = 5;
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public Api.NotificationFeed feed(String meterNo) throws SQLException {
        LocalDate today = Dates.today();
        return Database.withConnection(c -> {
            List<Notification> items = new ArrayList<>(reminders(new BillDao(c).search(meterNo, null, null), today));
            int unread = items.size();
            for (Notification n : new NotificationDao(c).recent(meterNo)) {
                items.add(n);
                unread += n.read() ? 0 : 1;
            }
            return new Api.NotificationFeed(unread, items);
        });
    }

    public void markAllRead(String meterNo) throws SQLException {
        Database.withConnection(c -> new NotificationDao(c).markAllRead(meterNo));
    }

    static List<Notification> reminders(List<Bill> bills, LocalDate today) {
        List<Notification> list = new ArrayList<>();
        for (Bill b : bills) {
            if (b.status() == BillStatus.PAID || b.dueDate() == null) {
                continue;
            }
            String period = b.month() + " " + b.year();
            long daysLeft = ChronoUnit.DAYS.between(today, b.dueDate());
            if (b.isOverdue(today)) {
                list.add(new Notification(0, Notification.Kind.OVERDUE, "Overdue: " + period + " bill",
                        "It was due on " + DAY.format(b.dueDate()) + ". You now owe " + Money.tk(b.amountDue(today))
                                + ", including the late fee. Pay soon to avoid disconnection.",
                        b.dueDate().plusDays(1).atStartOfDay(), false));
            } else if (daysLeft <= REMIND_DAYS) {
                String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
                String fee = b.totalAfterDue() == null ? "" : " to avoid the late fee (" + Money.tk(b.totalAfterDue()) + " after that)";
                list.add(new Notification(0, Notification.Kind.DUE_SOON, period + " bill is due " + when,
                        "Pay " + Money.tk(b.total()) + " by " + DAY.format(b.dueDate()) + fee + ".",
                        today.atStartOfDay(), false));
            }
        }
        return list;
    }
}
