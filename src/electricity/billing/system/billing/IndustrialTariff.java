package electricity.billing.system.billing;

import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.TariffPlan;
import java.util.List;

/**
 * Commercial and industrial tariff: every unit at one flat rate (the plan's
 * first slab). Larger connections in Bangladesh are billed by flat or
 * time-of-day rates rather than household slabs.
 */
public class IndustrialTariff implements Tariff {

    @Override
    public List<BillBreakdown.Line> energyLines(int units, TariffPlan plan) {
        if (units <= 0) {
            return List.of();
        }
        return List.of(Tariff.line("All units (flat rate)", units, plan.slabs().get(0).rate()));
    }
}
