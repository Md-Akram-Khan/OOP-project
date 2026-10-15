package electricity.billing.system.db;

import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Reads and writes the login table. */
public class UserDao {

    /** A login row including its password hash. Stays inside the service layer. */
    public record StoredUser(long id, String username, String name, Role role, String meterNo, String passwordHash) {

        public UserAccount toAccount() {
            return new UserAccount(username, name, role, meterNo);
        }
    }

    private final Connection c;

    public UserDao(Connection c) {
        this.c = c;
    }

    public Optional<StoredUser> findByUsername(String username, Role role) throws SQLException {
        String sql = "select id, username, name, \"user\", meter_no, password from login "
                + "where lower(username) = lower(?) and \"user\" = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, role.label());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("name"),
                        Role.fromLabel(rs.getString("user")),
                        rs.getString("meter_no"),
                        rs.getString("password")));
            }
        }
    }

    public void updatePasswordHash(long id, String hash) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("update login set password = ? where id = ?")) {
            ps.setString(1, hash);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public boolean adminExists() throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select 1 from login where \"user\" = ? limit 1")) {
            ps.setString(1, Role.ADMIN.label());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean usernameTaken(String username) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select 1 from login where lower(username) = lower(?)")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Stops two first-admin sign-ups from racing each other. Must be called inside a transaction. */
    public void lockForAccountCreation() throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("lock table login in share row exclusive mode")) {
            ps.execute();
        }
    }

    public void insertAdmin(String username, String name, String passwordHash) throws SQLException {
        String sql = "insert into login (meter_no, username, name, password, \"user\") values (null, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, name);
            ps.setString(3, passwordHash);
            ps.setString(4, Role.ADMIN.label());
            ps.executeUpdate();
        }
    }

    /** Reserves a login row for a new customer; the customer sets the username and password at sign-up. */
    public void insertCustomerPlaceholder(String meterNo, String name) throws SQLException {
        String sql = "insert into login (meter_no, username, name, password, \"user\") values (?, '', ?, '', ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            ps.setString(2, name);
            ps.setString(3, Role.CUSTOMER.label());
            ps.executeUpdate();
        }
    }

    /**
     * Sets the username and password on a customer's placeholder row.
     * Returns 0 if the meter already has a registered account, so an existing
     * account can never be overwritten.
     */
    public int registerCustomer(String meterNo, String username, String passwordHash) throws SQLException {
        String sql = "update login set username = ?, password = ? "
                + "where meter_no = ? and \"user\" = ? and coalesce(username, '') = ''";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, meterNo);
            ps.setString(4, Role.CUSTOMER.label());
            return ps.executeUpdate();
        }
    }
}
