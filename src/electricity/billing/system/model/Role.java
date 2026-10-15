package electricity.billing.system.model;

/** Who an account belongs to. The label is what is stored in login."user" and shown in the UI. */
public enum Role {
    ADMIN("Admin"),
    CUSTOMER("Customer");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Role fromLabel(String label) {
        for (Role role : values()) {
            if (role.label.equalsIgnoreCase(label)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + label);
    }

    @Override
    public String toString() {
        return label;
    }
}
