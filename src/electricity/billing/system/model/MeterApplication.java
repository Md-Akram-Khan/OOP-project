package electricity.billing.system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An application for a new electricity meter, from a phone- and NID-verified
 * applicant. Admins review it; once the meter is installed, connecting it
 * creates the customer and turns the applicant's account into a customer one.
 *
 * @param meterNo the meter connected for this application, once it's CONNECTED
 */
public record MeterApplication(
        long id,
        String username,
        String applicantName,
        String nidNo,
        String phone,
        String email,
        String house,
        String road,
        String area,
        String postOffice,
        String district,
        String division,
        BillType billType,
        BigDecimal loadKw,
        Phase phase,
        Ownership ownership,
        String notes,
        Status status,
        String adminNote,
        String meterNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public enum Status {
        SUBMITTED("Submitted"),
        UNDER_REVIEW("Under review"),
        APPROVED("Approved"),
        REJECTED("Rejected"),
        CONNECTED("Meter connected");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Still being handled: the applicant can't open another one meanwhile. */
        public boolean isOpen() {
            return this == SUBMITTED || this == UNDER_REVIEW || this == APPROVED;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Phase {
        SINGLE("Single phase"),
        THREE("Three phase");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Ownership {
        OWNER("Owner"),
        TENANT("Tenant");

        private final String label;

        Ownership(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** The reference shown to applicants, e.g. APP-00012. */
    public String reference() {
        return String.format("APP-%05d", id);
    }

    /** One line of address: house, road, area, post office. */
    public String address() {
        return house + ", " + road + ", " + area + ", " + postOffice;
    }
}
