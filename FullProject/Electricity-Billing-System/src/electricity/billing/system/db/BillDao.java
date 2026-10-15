package electricity.billing.system.db;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes the bill table. */
public class BillDao {

    private static final String SELECT = "select meter_no, month, year, units, totalbill, status from bill";
    private static final String NEWEST_FIRST = " order by year desc, array_position(array['January', 'February', "
            + "'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', "
            + "'December']::text[], month::text) desc, meter_no";

    private final Connection c;

    public BillDao(Connection c) {
        this.c = c;
    }

    /** Fails with a unique violation if the meter already has a bill for that month and year. */
    public void insert(Bill bill) throws SQLException {
        String sql = "insert into bill (meter_no, month, year, units, totalbill, status) values (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bill.meterNo());
            ps.setString(2, bill.month());
            ps.setInt(3, bill.year());
            ps.setInt(4, bill.units());
            ps.setBigDecimal(5, bill.total());
            ps.setString(6, bill.status().label());
            ps.executeUpdate();
        }
    }

    public Optional<Bill> find(String meterNo, String month, int year) throws SQLException {
        List<Bill> bills = search(meterNo, month, year);
        return bills.isEmpty() ? Optional.empty() : Optional.of(bills.get(0));
    }

    /** Any filter left null matches everything. */
    public List<Bill> search(String meterNo, String month, Integer year) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" where true");
        List<Object> params = new ArrayList<>();
        if (meterNo != null) {
            sql.append(" and meter_no = ?");
            params.add(meterNo);
        }
        if (month != null) {
            sql.append(" and month = ?");
            params.add(month);
        }
        if (year != null) {
            sql.append(" and year = ?");
            params.add(year);
        }
        sql.append(NEWEST_FIRST);

        List<Bill> bills = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bills.add(map(rs));
                }
            }
        }
        return bills;
    }

    /** Moves a bill to {@code to}, but only if it is currently in one of the {@code from} states. */
    public int updateStatus(String meterNo, String month, int year, BillStatus to, BillStatus... from)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "update bill set status = ? where meter_no = ? and month = ? and year = ? and status in (");
        for (int i = 0; i < from.length; i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(")");
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            ps.setString(1, to.label());
            ps.setString(2, meterNo);
            ps.setString(3, month);
            ps.setInt(4, year);
            for (int i = 0; i < from.length; i++) {
                ps.setString(5 + i, from[i].label());
            }
            return ps.executeUpdate();
        }
    }

    private static Bill map(ResultSet rs) throws SQLException {
        return new Bill(
                rs.getString("meter_no"),
                rs.getString("month"),
                rs.getInt("year"),
                rs.getInt("units"),
                rs.getBigDecimal("totalbill"),
                BillStatus.fromLabel(rs.getString("status")));
    }
}
