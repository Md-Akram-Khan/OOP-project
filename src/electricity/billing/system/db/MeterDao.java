package electricity.billing.system.db;

import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Meter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reads and writes the meter_info table. */
public class MeterDao {

    private final Connection c;

    public MeterDao(Connection c) {
        this.c = c;
    }

    /** Inserts the meter, or replaces its settings if it was already set up. */
    public void save(Meter meter) throws SQLException {
        String sql = "insert into meter_info (meter_no, meter_type, bill_type, days, sanctioned_load_kw, serial_no) "
                + "values (?, ?, ?, ?, ?, ?) on conflict (meter_no) do update set meter_type = excluded.meter_type, "
                + "bill_type = excluded.bill_type, days = excluded.days, sanctioned_load_kw = excluded.sanctioned_load_kw, "
                + "serial_no = excluded.serial_no";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meter.meterNo());
            ps.setString(2, meter.meterType());
            ps.setString(3, meter.billType().label());
            ps.setInt(4, meter.days());
            ps.setBigDecimal(5, meter.loadKw());
            ps.setString(6, meter.serialNo() == null || meter.serialNo().isBlank() ? null : meter.serialNo().trim());
            ps.executeUpdate();
        }
    }

    public Optional<Meter> find(String meterNo) throws SQLException {
        String sql = "select meter_no, meter_type, bill_type, days, sanctioned_load_kw, serial_no from meter_info where meter_no = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, meterNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Meter(
                        rs.getString("meter_no"),
                        rs.getString("meter_type"),
                        BillType.fromLabel(rs.getString("bill_type")),
                        rs.getInt("days"),
                        rs.getBigDecimal("sanctioned_load_kw"),
                        rs.getString("serial_no")));
            }
        }
    }

    /** Customer meter number to the number printed on that meter, for every meter that has one on file. */
    public Map<String, String> printedNumbers() throws SQLException {
        Map<String, String> numbers = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "select meter_no, serial_no from meter_info where serial_no is not null and serial_no <> ''");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                numbers.put(rs.getString(1), rs.getString(2));
            }
        }
        return numbers;
    }

    /** The meter number of the customer whose meter has this serial (letters and digits compared), or null. */
    public String serialOwner(String serial) throws SQLException {
        String sql = "select meter_no from meter_info where upper(regexp_replace(serial_no, '[^A-Za-z0-9]', '', 'g')) "
                + "= upper(regexp_replace(?, '[^A-Za-z0-9]', '', 'g'))";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, serial);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** The meter's settings, or the defaults for a home connection if it was never set up. */
    public Meter findOrDefault(String meterNo) throws SQLException {
        return find(meterNo).orElse(new Meter(meterNo, Meter.TYPES.get(0), BillType.NORMAL,
                Meter.DEFAULT_BILLING_DAYS, Meter.DEFAULT_LOAD_KW, null));
    }
}
