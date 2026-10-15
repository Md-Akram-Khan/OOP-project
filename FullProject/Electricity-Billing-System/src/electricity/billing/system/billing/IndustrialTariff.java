package electricity.billing.system.billing;

import electricity.billing.system.model.TaxRates;
import java.math.BigDecimal;

/** Industrial tariff: the per-unit price is scaled by the multiplier in the tax table. */
public class IndustrialTariff implements Tariff {

    @Override
    public BigDecimal energyCharge(int units, TaxRates rates) {
        return rates.costPerUnit()
                .multiply(rates.industrialMultiplier())
                .multiply(BigDecimal.valueOf(units));
    }
}
