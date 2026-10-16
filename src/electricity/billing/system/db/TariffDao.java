package electricity.billing.system.db;

import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Slab;
import electricity.billing.system.model.TariffPlan;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads the tariff_plan and tariff_slab tables. */
public class TariffDao {

    private static final String SELECT = "select id, bill_type, name, effective_from, lifeline_max_units, lifeline_rate, "
            + "demand_rate_per_kw, vat_percent, late_fee_percent, meter_rent, due_days, source from tariff_plan";

    private final Connection c;

    public TariffDao(Connection c) {
        this.c = c;
    }

    /** Every plan, newest first within each category. */
    public List<TariffPlan> all() throws SQLException {
        return query(SELECT + " order by bill_type, effective_from desc");
    }

    public Optional<TariffPlan> find(long id) throws SQLException {
        List<TariffPlan> plans = query(SELECT + " where id = ?", id);
        return plans.isEmpty() ? Optional.empty() : Optional.of(plans.get(0));
    }

    /**
     * The plan in force for a billing month: the newest one that had started by
     * the first of that month. Months older than every plan use the oldest plan.
     */
    public TariffPlan forMonth(BillType type, LocalDate monthStart) throws SQLException {
        List<TariffPlan> plans = query(SELECT + " where bill_type = ? order by (effective_from <= ?) desc, "
                + "case when effective_from <= ? then effective_from end desc, effective_from limit 1",
                type.label(), monthStart, monthStart);
        if (plans.isEmpty()) {
            throw new SQLException("No tariff is set up for " + type.displayName()
                    + " customers. Run database/migration-v4-bd-billing.sql to add the BERC tariffs.");
        }
        return plans.get(0);
    }

    private List<TariffPlan> query(String sql, Object... params) throws SQLException {
        List<TariffPlan> plans = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    plans.add(new TariffPlan(
                            id,
                            BillType.fromLabel(rs.getString("bill_type")),
                            rs.getString("name"),
                            rs.getObject("effective_from", LocalDate.class),
                            slabs(id),
                            (Integer) rs.getObject("lifeline_max_units"),
                            rs.getBigDecimal("lifeline_rate"),
                            rs.getBigDecimal("demand_rate_per_kw"),
                            rs.getBigDecimal("vat_percent"),
                            rs.getBigDecimal("late_fee_percent"),
                            rs.getBigDecimal("meter_rent"),
                            rs.getInt("due_days"),
                            rs.getString("source")));
                }
            }
        }
        return plans;
    }

    private List<Slab> slabs(long planId) throws SQLException {
        List<Slab> slabs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "select upto_units, rate from tariff_slab where plan_id = ? order by seq")) {
            ps.setLong(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slabs.add(new Slab((Integer) rs.getObject("upto_units"), rs.getBigDecimal("rate")));
                }
            }
        }
        if (slabs.isEmpty()) {
            throw new SQLException("Tariff plan " + planId + " has no slabs.");
        }
        return List.copyOf(slabs);
    }
}
