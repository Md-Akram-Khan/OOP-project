package electricity.billing.system.model;

import java.time.LocalDateTime;

/**
 * A message for a customer: a new bill, a payment received, a complaint
 * update or a usage alert. Due-date reminders are worked out when asked for
 * and are never stored; they have id 0 and stay until the bill is paid.
 */
public record Notification(long id, Kind kind, String title, String body, LocalDateTime createdAt, boolean read) {

    public enum Kind { BILL, PAYMENT, COMPLAINT, USAGE, DUE_SOON, OVERDUE }

    public boolean isReminder() {
        return kind == Kind.DUE_SOON || kind == Kind.OVERDUE;
    }
}
