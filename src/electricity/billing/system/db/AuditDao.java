package electricity.billing.system.db;

import electricity.billing.system.model.AuditEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Appends to and reads the audit_log table. Entries are never changed or deleted by the app. */
public class AuditDao {

    private final Connection c;

    public AuditDao(Connection c) {
        this.c = c;
    }

    public void insert(String actor, String action, String target, String details) throws SQLException {
        String sql = "insert into audit_log (actor, action, target, details) values (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cut(actor, 60));
            ps.setString(2, cut(action, 40));
            ps.setString(3, cut(target, 80));
            ps.setString(4, cut(details, 500));
            ps.executeUpdate();
        }
    }

    /** The newest entries first. */
    public List<AuditEntry> recent(int limit) throws SQLException {
        String sql = "select id, at, actor, action, target, details from audit_log order by at desc, id desc limit ?";
        List<AuditEntry> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AuditEntry(rs.getLong("id"), ComplaintDao.local(rs, "at"), rs.getString("actor"),
                            rs.getString("action"), rs.getString("target"), rs.getString("details")));
                }
            }
        }
        return list;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
