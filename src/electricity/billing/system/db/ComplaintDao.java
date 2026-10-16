package electricity.billing.system.db;

import electricity.billing.system.common.Dates;
import electricity.billing.system.model.Complaint;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes the complaint table. */
public class ComplaintDao {

    private static final String SELECT = "select k.id, k.meter_no, c.name, k.category, k.subject, k.details, k.status, "
            + "k.admin_reply, k.created_at, k.updated_at from complaint k join customer c on c.meter_no = k.meter_no";

    private final Connection c;

    public ComplaintDao(Connection c) {
        this.c = c;
    }

    /** Saves a new complaint and returns its id. */
    public long insert(String meterNo, Complaint.Category category, String subject, String details) throws SQLException {
        String sql = "insert into complaint (meter_no, category, subject, details) values (?, ?, ?, ?) returning id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            ps.setString(2, category.name());
            ps.setString(3, subject);
            ps.setString(4, details);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Newest first. A null meter number means every customer's complaints. */
    public List<Complaint> list(String meterNo) throws SQLException {
        String sql = SELECT + (meterNo == null ? "" : " where k.meter_no = ?") + " order by k.created_at desc, k.id desc";
        List<Complaint> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (meterNo != null) {
                ps.setString(1, meterNo);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public Optional<Complaint> find(long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " where k.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public int update(long id, Complaint.Status status, String reply) throws SQLException {
        String sql = "update complaint set status = ?, admin_reply = ?, updated_at = now() where id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, reply);
            ps.setLong(3, id);
            return ps.executeUpdate();
        }
    }

    private static Complaint map(ResultSet rs) throws SQLException {
        return new Complaint(
                rs.getLong("id"),
                rs.getString("meter_no"),
                rs.getString("name"),
                Complaint.Category.valueOf(rs.getString("category")),
                rs.getString("subject"),
                rs.getString("details"),
                Complaint.Status.valueOf(rs.getString("status")),
                rs.getString("admin_reply"),
                local(rs, "created_at"),
                local(rs, "updated_at"));
    }

    /** A timestamptz column as Bangladesh local time. */
    static LocalDateTime local(ResultSet rs, String column) throws SQLException {
        OffsetDateTime at = rs.getObject(column, OffsetDateTime.class);
        return at == null ? null : at.atZoneSameInstant(Dates.ZONE).toLocalDateTime();
    }
}
