package electricity.billing.system.model;

import java.math.BigDecimal;

/** The priced parts of a saved bill, kept so the bill reads the same even after rates change. */
public record BillCharges(
        long tariffPlanId,
        BigDecimal energy,
        BigDecimal loadKw,
        BigDecimal demand,
        BigDecimal vat,
        BigDecimal meterRent,
        BigDecimal lateFee) {
}
