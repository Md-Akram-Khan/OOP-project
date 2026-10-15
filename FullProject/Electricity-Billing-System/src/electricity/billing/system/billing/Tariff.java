package electricity.billing.system.billing;

import electricity.billing.system.model.BillType;
import electricity.billing.system.model.TaxRates;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a bill is priced. Each bill type supplies its own energy charge; the
 * fixed monthly charges are the same for everyone.
 */
public interface Tariff {

    /** Price of the units consumed, before fixed charges. */
    BigDecimal energyCharge(int units, TaxRates rates);

    default BigDecimal total(int units, TaxRates rates) {
        if (units < 0) {
            throw new IllegalArgumentException("Units consumed cannot be negative.");
        }
        return energyCharge(units, rates)
                .add(rates.fixedCharges())
                .setScale(2, RoundingMode.HALF_UP);
    }

    static Tariff forBillType(BillType type) {
        return switch (type) {
            case NORMAL -> new NormalTariff();
            case INDUSTRIAL -> new IndustrialTariff();
        };
    }
}
