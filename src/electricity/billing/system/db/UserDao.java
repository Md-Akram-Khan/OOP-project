package electricity.billing.system.db;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * The login with this username and role. Applicants (who don't have a meter
     * yet) sign in on the Customer side, so asking for CUSTOMER finds them too.
     */
    public Optional<StoredUser> findByUsername(String username, Role role) throws SQLException {
        String sql = "select id, username, name, \"user\", meter_no, password from login "
                + "where lower(username) = lower(?) and (\"user\" = ? or \"user\" = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, role.label());
            ps.setString(3, role == Role.CUSTOMER ? Role.APPLICANT.label() : role.label());
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

    /**
     * Every admin, by name, with their latest sign-in from the audit log
     * (null if they've never signed in since the log was added).
     */
    public List<Api.AdminInfo> admins(String currentUsername) throws SQLException {
        String sql = "select l.username, l.name, (select max(a.at) from audit_log a where a.action = 'ADMIN_LOGIN' "
                + "and lower(a.target) = lower(l.username)) as last_sign_in from login l where l.\"user\" = ? "
                + "order by lower(coalesce(nullif(l.name, ''), l.username))";
        List<Api.AdminInfo> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Role.ADMIN.label());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    list.add(new Api.AdminInfo(username, rs.getString("name"),
                            ComplaintDao.local(rs, "last_sign_in"), username.equalsIgnoreCase(currentUsername)));
                }
            }
        }
        return list;
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
    /** An applicant: phone and NID verified, no meter yet. */
    public void insertApplicant(String username, String name, String passwordHash, String phone, String nidNo)
            throws SQLException {
        String sql = "insert into login (meter_no, username, name, password, \"user\", phone, nid_no) values (null, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, name);
            ps.setString(3, passwordHash);
            ps.setString(4, Role.APPLICANT.label());
            ps.setString(5, phone);
            ps.setString(6, nidNo);
            ps.executeUpdate();
        }
    }

    public boolean nidRegistered(String nidNo) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select 1 from login where nid_no = ?")) {
            ps.setString(1, nidNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** An applicant's login id and verified details, by username. */
    public Optional<Applicant> applicant(String username) throws SQLException {
        String sql = "select id, username, name, phone, nid_no from login where lower(username) = lower(?) and \"user\" = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, Role.APPLICANT.label());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Applicant(rs.getLong("id"), rs.getString("username"), rs.getString("name"),
                        rs.getString("phone"), rs.getString("nid_no"))) : Optional.empty();
            }
        }
    }

    public record Applicant(long id, String username, String name, String phone, String nidNo) {
    }

    /** Once their meter is connected, an applicant's login becomes that meter's customer login. */
    public int makeCustomer(long loginId, String meterNo) throws SQLException {
        String sql = "update login set \"user\" = ?, meter_no = ? where id = ? and \"user\" = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Role.CUSTOMER.label());
            ps.setString(2, meterNo);
            ps.setLong(3, loginId);
            ps.setString(4, Role.APPLICANT.label());
            return ps.executeUpdate();
        }
    }

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
