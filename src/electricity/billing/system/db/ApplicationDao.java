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

    /** Every column an application is written to, in the order {@link #insert} fills them. */
    private static final String[] COLUMNS = {"login_id", "applicant_name", "nid_no", "phone", "date_of_birth", "consumer_type",
        "father_name", "mother_name", "spouse_name", "gender", "organization_name",
        "m_house", "m_road", "m_section", "m_block", "m_thana", "m_district", "m_division", "m_post_code", "m_email",
        "m_telephone", "m_mobile",
        "house", "road", "section", "block", "thana", "district", "division", "post_code", "email", "telephone", "mobile",
        "area", "nocs", "connection_type", "phase", "tariff_category", "bill_type", "meter_count", "load_kw",
        "different_owners", "existing_meters", "existing_load_kw", "existing_customer_no", "ownership", "notes",
        "declared_at"};

    public long insert(long loginId, MeterApplication a) throws SQLException {
        String sql = "insert into meter_application (" + String.join(", ", COLUMNS) + ") values ("
                + "?, ".repeat(COLUMNS.length - 1) + "now()) returning id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setLong(i++, loginId);
            ps.setString(i++, a.applicantName());
            ps.setString(i++, a.nidNo());
            ps.setString(i++, a.phone());
            ps.setObject(i++, a.dateOfBirth());
            ps.setString(i++, a.consumerType().name());
            ps.setString(i++, a.fatherName());
            ps.setString(i++, a.motherName());
            ps.setString(i++, a.spouseName());
            ps.setString(i++, a.gender() == null ? null : a.gender().name());
            ps.setString(i++, a.organizationName());
            i = setAddress(ps, i, a.mailing());
            i = setAddress(ps, i, a.connection());
            ps.setString(i++, a.area());
            ps.setString(i++, a.nocs());
            ps.setString(i++, a.connectionType().name());
            ps.setString(i++, a.phase().name());
            ps.setString(i++, a.tariffCategory());
            ps.setString(i++, a.billType().label());
            ps.setInt(i++, a.meterCount());
            ps.setBigDecimal(i++, a.loadKw());
            ps.setObject(i++, a.differentOwners());
            ps.setObject(i++, a.existingMeters());
            ps.setBigDecimal(i++, a.existingLoadKw());
            ps.setString(i++, a.existingCustomerNo());
            ps.setString(i++, a.ownership().name());
            ps.setString(i, a.notes());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static int setAddress(PreparedStatement ps, int i, MeterApplication.Address a) throws SQLException {
        for (String value : new String[] {a.house(), a.road(), a.section(), a.block(), a.thana(), a.district(), a.division(),
            a.postCode(), a.email(), a.telephone(), a.mobile()}) {
            ps.setString(i++, value);
        }
        return i;
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
        String gender = rs.getString("gender");
        Integer existingMeters = (Integer) rs.getObject("existing_meters");
        // Applications made before the full form have no mailing address, and a post office instead of a post code.
        MeterApplication.Address mailing = rs.getString("m_house") == null ? null : new MeterApplication.Address(
                rs.getString("m_house"), rs.getString("m_road"), rs.getString("m_section"), rs.getString("m_block"),
                rs.getString("m_thana"), rs.getString("m_district"), rs.getString("m_division"), rs.getString("m_post_code"),
                rs.getString("m_email"), rs.getString("m_telephone"), rs.getString("m_mobile"));
        String postCode = rs.getString("post_code");
        MeterApplication.Address connection = new MeterApplication.Address(rs.getString("house"), rs.getString("road"),
                rs.getString("section"), rs.getString("block"), rs.getString("thana"), rs.getString("district"),
                rs.getString("division"), postCode != null ? postCode : rs.getString("post_office"), rs.getString("email"),
                rs.getString("telephone"), rs.getString("mobile"));
        return new MeterApplication(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("applicant_name"),
                rs.getString("nid_no"),
                rs.getString("phone"),
                rs.getObject("date_of_birth", java.time.LocalDate.class),
                MeterApplication.ConsumerType.valueOf(rs.getString("consumer_type")),
                rs.getString("father_name"),
                rs.getString("mother_name"),
                rs.getString("spouse_name"),
                gender == null ? null : MeterApplication.Gender.valueOf(gender),
                rs.getString("organization_name"),
                mailing,
                connection,
                rs.getString("area"),
                rs.getString("nocs"),
                MeterApplication.ConnectionType.valueOf(rs.getString("connection_type")),
                MeterApplication.Phase.valueOf(rs.getString("phase")),
                rs.getString("tariff_category"),
                BillType.fromLabel(rs.getString("bill_type")),
                rs.getInt("meter_count"),
                rs.getBigDecimal("load_kw"),
                (Boolean) rs.getObject("different_owners"),
                existingMeters,
                rs.getBigDecimal("existing_load_kw"),
                rs.getString("existing_customer_no"),
                MeterApplication.Ownership.valueOf(rs.getString("ownership")),
                rs.getString("notes"),
                MeterApplication.Status.valueOf(rs.getString("status")),
                rs.getString("admin_note"),
                rs.getString("meter_no"),
                ComplaintDao.local(rs, "created_at"),
                ComplaintDao.local(rs, "updated_at"));
    }
}
