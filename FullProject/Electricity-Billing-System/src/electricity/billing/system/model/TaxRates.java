package electricity.billing.system.model;

import java.math.BigDecimal;

/** The single row of the tax table: per-unit price plus fixed monthly charges. */
public record TaxRates(
        BigDecimal costPerUnit,
        BigDecimal meterRent,
        BigDecimal serviceCharge,
        BigDecimal serviceTax,
        BigDecimal fixedTax,
        BigDecimal industrialMultiplier) {

    /** Charges added to every bill regardless of units consumed. */
    public BigDecimal fixedCharges() {
        return meterRent.add(serviceCharge).add(serviceTax).add(fixedTax);
    }
}
