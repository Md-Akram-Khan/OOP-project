package electricity.billing.system.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/** Stores and reads the meter photo each bill was made from. */
public class MeterPhotoDao {

    /** A stored photo and what was read from it. */
    public record StoredPhoto(byte[] image, String contentType, String displayText, String serialSeen, String uploadedBy,
            LocalDateTime uploadedAt) {
    }

    private final Connection c;

    public MeterPhotoDao(Connection c) {
        this.c = c;
    }

    /** Fails with a unique violation if the very same photo was already used for a bill. */
    public void insert(String meterNo, int year, String month, byte[] image, String contentType, String sha256,
            String displayText, String serialSeen, String uploadedBy) throws SQLException {
        String sql = "insert into meter_photo (meter_no, year, month, image, content_type, sha256, display_text, serial_seen, "
                + "uploaded_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            ps.setInt(2, year);
            ps.setString(3, month);
            ps.setBytes(4, image);
            ps.setString(5, contentType);
            ps.setString(6, sha256);
            ps.setString(7, displayText);
            ps.setString(8, serialSeen);
            ps.setString(9, uploadedBy);
            ps.executeUpdate();
        }
    }

    public boolean shaUsed(String sha256) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select 1 from meter_photo where sha256 = ?")) {
            ps.setString(1, sha256);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Optional<StoredPhoto> find(String meterNo, String month, int year) throws SQLException {
        String sql = "select image, content_type, display_text, serial_seen, uploaded_by, uploaded_at from meter_photo "
                + "where meter_no = ? and month = ? and year = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            ps.setString(2, month);
            ps.setInt(3, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredPhoto(rs.getBytes("image"), rs.getString("content_type"),
                        rs.getString("display_text"), rs.getString("serial_seen"), rs.getString("uploaded_by"),
                        ComplaintDao.local(rs, "uploaded_at")));
            }
        }
    }
}
