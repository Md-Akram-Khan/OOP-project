package electricity.billing.system.model;

import java.time.LocalDateTime;

/** A customer's complaint or service request, and the admin's answer. */
public record Complaint(
        long id,
        String meterNo,
        String customerName,
        Category category,
        String subject,
        String details,
        Status status,
        String adminReply,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public enum Category {
        BILLING("Wrong or high bill"),
        METER("Meter problem"),
        OUTAGE("Power outage"),
        CONNECTION("New or changed connection"),
        OTHER("Something else");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Status {
        OPEN("Open"),
        IN_PROGRESS("In progress"),
        RESOLVED("Resolved");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** The reference customers quote, e.g. CMP-00012. */
    public String ticket() {
        return ticket(id);
    }

    public static String ticket(long id) {
        return String.format("CMP-%05d", id);
    }
}
