package electricity.billing.system.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An application for a new electricity meter, from a phone- and NID-verified
 * applicant, with the same details DPDC's new-connection form asks for.
 * Admins review it; once the meter is installed, connecting it creates the
 * customer and turns the applicant's account into a customer one.
 *
 * The applicant's name, NID number, date of birth and phone come from their
 * verification. For an organization they are the proprietor's.
 *
 * @param mailing    where letters go; null on applications made before the full form
 * @param connection where the meter is to be installed
 * @param area       the connection's area
 * @param nocs       the local office (Network Operation and Customer Service), if the applicant gave it
 * @param billType   how the connection is billed, from its tariff category
 * @param meterNo    the meter connected for this application, once it's CONNECTED
 */
public record MeterApplication(
        long id,
        String username,
        String applicantName,
        String nidNo,
        String phone,
        LocalDate dateOfBirth,
        ConsumerType consumerType,
        String fatherName,
        String motherName,
        String spouseName,
        Gender gender,
        String organizationName,
        Address mailing,
        Address connection,
        String area,
        String nocs,
        ConnectionType connectionType,
        Phase phase,
        String tariffCategory,
        BillType billType,
        int meterCount,
        BigDecimal loadKw,
        Boolean differentOwners,
        Integer existingMeters,
        BigDecimal existingLoadKw,
        String existingCustomerNo,
        Ownership ownership,
        String notes,
        Status status,
        String adminNote,
        String meterNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** One address on the form. Optional parts are null when left blank. */
    public record Address(String house, String road, String section, String block, String thana, String district,
            String division, String postCode, String email, String telephone, String mobile) {

        /** House, road, section, block, thana, district and post code on one line. */
        public String oneLine() {
            List<String> parts = new ArrayList<>();
            add(parts, house);
            add(parts, road);
            add(parts, section == null || section.isBlank() ? null : "Section " + section);
            add(parts, block == null || block.isBlank() ? null : "Block " + block);
            add(parts, thana);
            add(parts, district == null ? null : district + (postCode == null || postCode.isBlank() ? "" : "-" + postCode));
            return String.join(", ", parts);
        }

        private static void add(List<String> parts, String value) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
    }

    public enum ConsumerType {
        PERSONAL("Personal"),
        ORGANIZATION("Organization / Institute");

        private final String label;

        ConsumerType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Gender {
        MALE("Male"),
        FEMALE("Female"),
        THIRD("Third gender");

        private final String label;

        Gender(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ConnectionType {
        PERMANENT("Permanent"),
        TEMPORARY("Temporary");

        private final String label;

        ConnectionType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

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

    /** Who the connection is for: the organization, or the applicant. */
    public String consumerName() {
        return consumerType == ConsumerType.ORGANIZATION && organizationName != null ? organizationName : applicantName;
    }

    /** The connection address on one line, with its area. */
    public String address() {
        String line = connection.oneLine();
        return area == null || area.isBlank() || line.contains(area) ? line : line + " (" + area + ")";
    }

    /** "LT-A · 3.5 kW" style category, or the bill type for applications made before tariff categories. */
    public String categoryLabel() {
        return tariffCategory != null ? tariffCategory : billType.displayName();
    }
}
