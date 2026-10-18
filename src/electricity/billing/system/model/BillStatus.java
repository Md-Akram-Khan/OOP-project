package electricity.billing.system.model;

/**
 * Lifecycle of a bill: NOT_PAID until SSLCommerz confirms an online payment,
 * then PAID. Nobody marks a bill paid by hand. PENDING is only kept so bills
 * saved by older versions (a customer's self-reported payment) still load;
 * the app treats them as unpaid.
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
