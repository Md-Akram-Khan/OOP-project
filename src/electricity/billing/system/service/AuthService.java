package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.UserDao.StoredUser;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import electricity.billing.system.security.PasswordHasher;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Logging in and creating accounts. */
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,30}");

    public UserAccount login(String username, char[] password, Role role) throws SQLException {
        if (username.isBlank() || password.length == 0) {
            throw new AppException("Enter your username and password.");
        }
        return Database.withConnection(c -> {
            UserDao users = new UserDao(c);
            StoredUser user = users.findByUsername(username.trim(), role)
                    .filter(u -> PasswordHasher.verify(password, u.passwordHash()))
                    .orElseThrow(() -> new AppException("Invalid username or password."));
            if (PasswordHasher.needsRehash(user.passwordHash())) {
                users.updatePasswordHash(user.id(), PasswordHasher.hash(password));
            }
            return user.toAccount();
        });
    }

    /** Every administrator, for the Team page. Only admins may ask. */
    public List<Api.AdminInfo> admins(UserAccount requestedBy) throws SQLException {
        return Database.withConnection(c -> new UserDao(c).admins(requestedBy.username()));
    }

    public boolean adminExists() throws SQLException {
        return Database.withConnection(c -> new UserDao(c).adminExists());
    }

    /**
     * Creates an admin account. Allowed when an admin requests it, or from the
     * public sign-up screen only while no admin exists yet (first-time set-up).
     */
    public void createAdmin(String username, String name, char[] password, UserAccount requestedBy)
            throws SQLException {
        validateCredentials(username, password);
        if (name.isBlank()) {
            throw new AppException("Enter a name.");
        }
        boolean byAdmin = requestedBy != null && requestedBy.isAdmin();
        String hash = PasswordHasher.hash(password);
        Database.inTransaction(c -> {
            UserDao users = new UserDao(c);
            users.lockForAccountCreation();
            if (!byAdmin && users.adminExists()) {
                throw new AppException("An admin account already exists. Ask an admin to create new admin accounts.");
            }
            ensureUsernameFree(users, username);
            translateDuplicateUsername(() -> users.insertAdmin(username, name, hash));
            return null;
        });
    }

    /**
     * Lets a customer claim the account the admin reserved for their meter. They
     * must prove who they are with the email on file, and an account that has
     * already been claimed can never be overwritten.
     */
    public void registerCustomer(String meterNo, String email, String username, char[] password)
            throws SQLException {
        if (meterNo.isBlank() || email.isBlank()) {
            throw new AppException("Enter your meter number and the email address you registered with.");
        }
        validateCredentials(username, password);
        String hash = PasswordHasher.hash(password);
        String meter = meterNo.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        Database.inTransaction(c -> {
            Customer customer = new CustomerDao(c).find(meter)
                    .filter(found -> found.email() != null && found.email().trim().equalsIgnoreCase(email))
                    .orElseThrow(() -> new AppException("The meter number and email don't match our records."));
            UserDao users = new UserDao(c);
            ensureUsernameFree(users, username);
            int[] updated = new int[1];
            translateDuplicateUsername(() -> updated[0] = users.registerCustomer(customer.meterNo(), username, hash));
            if (updated[0] == 0) {
                throw new AppException("This meter already has an account. Log in with that account instead.");
            }
            return null;
        });
    }

    static void validateCredentials(String username, char[] password) {
        if (!USERNAME.matcher(username).matches()) {
            throw new AppException("Usernames must be 3-30 characters: letters, numbers, dots, dashes or underscores.");
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw new AppException("Passwords must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private static void ensureUsernameFree(UserDao users, String username) throws SQLException {
        if (users.usernameTaken(username)) {
            throw new AppException("That username is already taken.");
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private static void translateDuplicateUsername(SqlAction action) throws SQLException {
        try {
            action.run();
        } catch (SQLException e) {
            if (Database.isUniqueViolation(e)) {
                throw new AppException("That username is already taken.");
            }
            throw e;
        }
    }
}
