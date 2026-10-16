package electricity.billing.system.db;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillCharges;
import electricity.billing.system.model.BillStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Reads and writes the bill table. */
public class BillDao {

    private static final String SELECT = "select meter_no, month, year, units, totalbill, status, previous_reading, "
            + "current_reading, tariff_plan_id, energy_charge, load_kw, demand_charge, vat, meter_rent, late_fee, "
            + "total_after_due, due_date, issued_on, warnings from bill";
    private static final String NEWEST_FIRST = " order by year desc, array_position(array['January', 'February', "
            + "'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', "
            + "'December']::text[], month::text) desc, meter_no";

    private final Connection c;

    public BillDao(Connection c) {
        this.c = c;
    }

    /** Fails with a unique violation if the meter already has a bill for that month and year. */
    public void insert(Bill bill) throws SQLException {
        String sql = "insert into bill (meter_no, month, year, units, totalbill, status, previous_reading, current_reading, "
                + "tariff_plan_id, energy_charge, load_kw, demand_charge, vat, meter_rent, late_fee, total_after_due, "
                + "due_date, issued_on, warnings) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        BillCharges ch = bill.charges();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bill.meterNo());
            ps.setString(2, bill.month());
            ps.setInt(3, bill.year());
            ps.setInt(4, bill.units());
            ps.setBigDecimal(5, bill.total());
            ps.setString(6, bill.status().label());
            ps.setObject(7, bill.previousReading(), Types.INTEGER);
            ps.setObject(8, bill.currentReading(), Types.INTEGER);
            ps.setObject(9, ch == null ? null : ch.tariffPlanId(), Types.BIGINT);
            ps.setBigDecimal(10, ch == null ? null : ch.energy());
            ps.setBigDecimal(11, ch == null ? null : ch.loadKw());
            ps.setBigDecimal(12, ch == null ? null : ch.demand());
            ps.setBigDecimal(13, ch == null ? null : ch.vat());
            ps.setBigDecimal(14, ch == null ? null : ch.meterRent());
            ps.setBigDecimal(15, ch == null ? null : ch.lateFee());
            ps.setBigDecimal(16, bill.totalAfterDue());
            ps.setObject(17, bill.dueDate(), Types.DATE);
            ps.setObject(18, bill.issuedOn(), Types.DATE);
            ps.setString(19, bill.hasWarnings() ? String.join("\n", bill.warnings()) : null);
            ps.executeUpdate();
        }
    }

    public Optional<Bill> find(String meterNo, String month, int year) throws SQLException {
        List<Bill> bills = search(meterNo, month, year);
        return bills.isEmpty() ? Optional.empty() : Optional.of(bills.get(0));
    }

    /** Any filter left null matches everything. Newest bill first. */
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

    /** The meter's newest bill that recorded a meter reading. */
    public Optional<Bill> lastReading(String meterNo) throws SQLException {
        return search(meterNo, null, null).stream().filter(b -> b.currentReading() != null).findFirst();
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
        long planId = rs.getLong("tariff_plan_id");
        BillCharges charges = rs.wasNull() ? null : new BillCharges(
                planId,
                rs.getBigDecimal("energy_charge"),
                rs.getBigDecimal("load_kw"),
                rs.getBigDecimal("demand_charge"),
                rs.getBigDecimal("vat"),
                rs.getBigDecimal("meter_rent"),
                rs.getBigDecimal("late_fee"));
        String warnings = rs.getString("warnings");
        return new Bill(
                rs.getString("meter_no"),
                rs.getString("month"),
                rs.getInt("year"),
                rs.getInt("units"),
                rs.getBigDecimal("totalbill"),
                BillStatus.fromLabel(rs.getString("status")),
                (Integer) rs.getObject("previous_reading"),
                (Integer) rs.getObject("current_reading"),
                charges,
                rs.getBigDecimal("total_after_due"),
                rs.getObject("due_date", LocalDate.class),
                rs.getObject("issued_on", LocalDate.class),
                warnings == null || warnings.isBlank() ? List.of() : Arrays.asList(warnings.split("\n")));
    }
}
