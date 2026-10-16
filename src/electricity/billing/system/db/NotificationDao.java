package electricity.billing.system.db;

import electricity.billing.system.model.Notification;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes the notification table. */
public class NotificationDao {

    private static final int KEEP = 50;

    private final Connection c;

    public NotificationDao(Connection c) {
        this.c = c;
    }

    public void insert(String meterNo, Notification.Kind kind, String title, String body) throws SQLException {
        String sql = "insert into notification (meter_no, kind, title, body) values (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            ps.setString(2, kind.name());
            ps.setString(3, title);
            ps.setString(4, body);
            ps.executeUpdate();
        }
    }

    /** The meter's latest notifications, newest first. */
    public List<Notification> recent(String meterNo) throws SQLException {
        String sql = "select id, kind, title, body, created_at, read_at is not null as is_read from notification "
                + "where meter_no = ? order by created_at desc, id desc limit " + KEEP;
        List<Notification> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Notification(rs.getLong("id"), Notification.Kind.valueOf(rs.getString("kind")),
                            rs.getString("title"), rs.getString("body"), ComplaintDao.local(rs, "created_at"),
                            rs.getBoolean("is_read")));
                }
            }
        }
        return list;
    }

    public int markAllRead(String meterNo) throws SQLException {
        String sql = "update notification set read_at = now() where meter_no = ? and read_at is null";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            return ps.executeUpdate();
        }
    }
}
