package electricity.billing.system.db;

import electricity.billing.system.model.BillType;
import electricity.billing.system.model.MeterApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes the meter_application table. */
public class ApplicationDao {

    private static final String SELECT = "select a.*, l.username from meter_application a join login l on l.id = a.login_id";

    private final Connection c;

    public ApplicationDao(Connection c) {
        this.c = c;
    }

    public long insert(long loginId, MeterApplication a) throws SQLException {
        String sql = "insert into meter_application (login_id, applicant_name, nid_no, phone, email, house, road, area, "
                + "post_office, district, division, bill_type, load_kw, phase, ownership, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, loginId);
            ps.setString(2, a.applicantName());
            ps.setString(3, a.nidNo());
            ps.setString(4, a.phone());
            ps.setString(5, a.email());
            ps.setString(6, a.house());
            ps.setString(7, a.road());
            ps.setString(8, a.area());
            ps.setString(9, a.postOffice());
            ps.setString(10, a.district());
            ps.setString(11, a.division());
            ps.setString(12, a.billType().label());
            ps.setBigDecimal(13, a.loadKw());
            ps.setString(14, a.phase().name());
            ps.setString(15, a.ownership().name());
            ps.setString(16, a.notes());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Newest first. A null login id means everyone's. */
    public List<MeterApplication> list(Long loginId) throws SQLException {
        String sql = SELECT + (loginId == null ? "" : " where a.login_id = ?") + " order by a.created_at desc, a.id desc";
        List<MeterApplication> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (loginId != null) {
                ps.setLong(1, loginId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public Optional<MeterApplication> find(long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT + " where a.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** The login an application belongs to. */
    public long loginId(long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select login_id from meter_application where id = ? for update")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No application " + id);
                }
                return rs.getLong(1);
            }
        }
    }

    public int update(long id, MeterApplication.Status status, String adminNote, String meterNo) throws SQLException {
        String sql = "update meter_application set status = ?, admin_note = ?, meter_no = coalesce(?, meter_no), "
                + "updated_at = now() where id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, adminNote);
            ps.setString(3, meterNo);
            ps.setLong(4, id);
            return ps.executeUpdate();
        }
    }

    private static MeterApplication map(ResultSet rs) throws SQLException {
        return new MeterApplication(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("applicant_name"),
                rs.getString("nid_no"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("house"),
                rs.getString("road"),
                rs.getString("area"),
                rs.getString("post_office"),
                rs.getString("district"),
                rs.getString("division"),
                BillType.fromLabel(rs.getString("bill_type")),
                rs.getBigDecimal("load_kw"),
                MeterApplication.Phase.valueOf(rs.getString("phase")),
                MeterApplication.Ownership.valueOf(rs.getString("ownership")),
                rs.getString("notes"),
                MeterApplication.Status.valueOf(rs.getString("status")),
                rs.getString("admin_note"),
                rs.getString("meter_no"),
                ComplaintDao.local(rs, "created_at"),
                ComplaintDao.local(rs, "updated_at"));
    }
}
