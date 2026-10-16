package electricity.billing.system.service;

import electricity.billing.system.db.AuditDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.model.AuditEntry;
import electricity.billing.system.model.UserAccount;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/** A permanent record of every change to customers, bills, payments and complaints. */
public class AuditService {

    private static final Logger LOG = Logger.getLogger(AuditService.class.getName());
    private static final int MAX_ENTRIES = 500;

    /** "admin1 (Admin)" or "ayesha (Customer 123456)". */
    public static String actor(UserAccount user) {
        if (user == null) {
            return "Anonymous";
        }
        return user.username() + (user.isAdmin() ? " (Admin)" : " (Customer " + user.meterNo() + ")");
    }

    /**
     * Records an action that has already succeeded. A failure to write the log
     * is reported in the server console but doesn't undo the action.
     */
    public void record(UserAccount user, String action, String target, String details) {
        record(actor(user), action, target, details);
    }

    public void record(String actor, String action, String target, String details) {
        try {
            Database.withConnection(c -> {
                new AuditDao(c).insert(actor, action, target, details);
                return null;
            });
        } catch (SQLException e) {
            LOG.warning("Couldn't write to the audit log (" + action + " " + target + "): " + e.getMessage());
        }
    }

    public List<AuditEntry> recent(int limit) throws SQLException {
        int capped = Math.max(1, Math.min(limit, MAX_ENTRIES));
        return Database.withConnection(c -> new AuditDao(c).recent(capped));
    }
}
