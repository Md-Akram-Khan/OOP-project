package electricity.billing.system.model;

import java.math.BigDecimal;

/**
 * One band of a telescopic tariff: units up to {@code uptoUnits} (inclusive)
 * cost {@code rate} taka each. The last band has no upper limit (null).
 */
public record Slab(Integer uptoUnits, BigDecimal rate) {
}
