package electricity.billing.system.billing;

import electricity.billing.system.model.TaxRates;
import java.math.BigDecimal;

/** Domestic tariff: units x cost per unit. */
public class NormalTariff implements Tariff {

    @Override
    public BigDecimal energyCharge(int units, TaxRates rates) {
        return rates.costPerUnit().multiply(BigDecimal.valueOf(units));
    }
}
