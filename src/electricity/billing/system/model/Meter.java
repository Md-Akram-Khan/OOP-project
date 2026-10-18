package electricity.billing.system.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Meter set-up for a customer.
 *
 * @param sanctionedLoadKw the load the connection is approved for; the monthly
 *                         demand charge is this many kW times the tariff's rate
 * @param serialNo         the serial printed on the physical meter (e.g. "E-756216");
 *                         a meter photo must show it before a bill can be made
 */
public record Meter(String meterNo, String meterType, BillType billType, int days, BigDecimal sanctionedLoadKw,
        String serialNo) {

    public static final int DEFAULT_BILLING_DAYS = 30;
    public static final BigDecimal DEFAULT_LOAD_KW = new BigDecimal("2.00");
    public static final BigDecimal MAX_LOAD_KW = new BigDecimal("5000");
    public static final List<String> TYPES = List.of("Electric Meter", "Solar Meter", "Smart Meter");

    /** The load to bill on, falling back to the 2 kW typical of a home connection. */
    public BigDecimal loadKw() {
        return sanctionedLoadKw == null || sanctionedLoadKw.signum() <= 0 ? DEFAULT_LOAD_KW : sanctionedLoadKw;
    }
}
