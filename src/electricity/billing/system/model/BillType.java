package electricity.billing.system.model;

/** The tariff a meter is billed on. */
public enum BillType {
    NORMAL("Normal"),
    INDUSTRIAL("Industrial");

    private final String label;

    BillType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BillType fromLabel(String label) {
        // "Industial" is the misspelling older versions of the app saved.
        if ("Industial".equalsIgnoreCase(label)) {
            return INDUSTRIAL;
        }
        for (BillType type : values()) {
            if (type.label.equalsIgnoreCase(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown bill type: " + label);
    }

    @Override
    public String toString() {
        return label;
    }
}
