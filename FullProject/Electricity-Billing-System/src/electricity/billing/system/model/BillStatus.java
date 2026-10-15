package electricity.billing.system.model;

/**
 * Lifecycle of a bill: a customer submits a payment (PENDING) and an admin
 * confirms it (PAID). A customer can never mark their own bill as paid.
 */
public enum BillStatus {
    NOT_PAID("Not Paid"),
    PENDING("Pending Verification"),
    PAID("Paid");

    private final String label;

    BillStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BillStatus fromLabel(String label) {
        for (BillStatus status : values()) {
            if (status.label.equalsIgnoreCase(label)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown bill status: " + label);
    }

    @Override
    public String toString() {
        return label;
    }
}
