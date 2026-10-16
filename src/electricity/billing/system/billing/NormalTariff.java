package electricity.billing.system.billing;

import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.Slab;
import electricity.billing.system.model.TariffPlan;
import java.util.ArrayList;
import java.util.List;

/**
 * Residential (LT-A) tariff: telescopic slabs. Each band of units is charged at
 * its own rate, so only the units above 400 pay the fifth-slab rate.
 *
 * A household whose whole month stays within the lifeline band (0–50 units)
 * pays the lifeline rate for every unit instead.
 */
public class NormalTariff implements Tariff {

    @Override
    public List<BillBreakdown.Line> energyLines(int units, TariffPlan plan) {
        List<BillBreakdown.Line> lines = new ArrayList<>();
        if (units <= 0) {
            return lines;
        }
        if (plan.qualifiesForLifeline(units)) {
            lines.add(Tariff.line("Lifeline 0–" + plan.lifelineMaxUnits(), units, plan.lifelineRate()));
            return lines;
        }
        int billed = 0;
        for (int i = 0; i < plan.slabs().size() && billed < units; i++) {
            Slab slab = plan.slabs().get(i);
            int bandEnd = slab.uptoUnits() == null ? units : Math.min(units, slab.uptoUnits());
            int inBand = bandEnd - billed;
            if (inBand > 0) {
                lines.add(Tariff.line(plan.slabLabel(i), inBand, slab.rate()));
                billed = bandEnd;
            }
        }
        return lines;
    }
}
