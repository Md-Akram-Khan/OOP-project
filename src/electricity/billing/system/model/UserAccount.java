package electricity.billing.system.model;

/** The signed-in user. Never carries the password hash. */
public record UserAccount(String username, String name, Role role, String meterNo) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
