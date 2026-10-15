package electricity.billing.system.db;

import electricity.billing.system.model.TaxRates;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Reads the single-row tax table. */
public class TaxDao {

    private final Connection c;

    public TaxDao(Connection c) {
        this.c = c;
    }

    public TaxRates load() throws SQLException {
        String sql = "select cost_per_unit, meter_rent, service_charge, service_tax, fixed_tax, industrial_multiplier "
                + "from tax where id = 1";
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Tax rates are not configured. Run supabase-schema.sql to add them.");
            }
            return new TaxRates(
                    rs.getBigDecimal("cost_per_unit"),
                    rs.getBigDecimal("meter_rent"),
                    rs.getBigDecimal("service_charge"),
                    rs.getBigDecimal("service_tax"),
                    rs.getBigDecimal("fixed_tax"),
                    rs.getBigDecimal("industrial_multiplier"));
        }
    }
}
