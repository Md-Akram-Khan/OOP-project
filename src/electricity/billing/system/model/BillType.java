package electricity.billing.system.model;

/**
 * The customer category a meter is billed under. Each category has its own
 * tariff plan; {@link #label()} is the value stored in the database.
 */
public enum BillType {
    NORMAL("Normal", "Residential", "LT-A domestic: telescopic slab tariff with a lifeline band"),
    INDUSTRIAL("Industrial", "Commercial & industrial", "Shops, offices and industry: one flat rate per unit");

    private final String label;
    private final String displayName;
    private final String description;

    BillType(String label, String displayName, String description) {
        this.label = label;
        this.displayName = displayName;
        this.description = description;
    }

    /** The value stored in meter_info.bill_type. */
    public String label() {
        return label;
    }

    /** What the app shows, e.g. "Residential". */
    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
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
        return displayName;
    }
}
