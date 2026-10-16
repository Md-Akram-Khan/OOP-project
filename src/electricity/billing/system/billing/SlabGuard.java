package electricity.billing.system.billing;

import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Slab;
import electricity.billing.system.model.SlabPosition;
import electricity.billing.system.model.TariffPlan;

/**
 * Tells a household how close it is to a dearer slab. The jump that matters
 * most in Bangladesh is at 400 units, where the rate goes from Tk 9.62 to
 * Tk 15.01: about 56% more for every extra unit.
 */
public final class SlabGuard {

    private SlabGuard() {
    }

    public static SlabPosition position(int units, TariffPlan plan) {
        if (plan.billType() != BillType.NORMAL) {
            Slab flat = plan.slabs().get(0);
            return new SlabPosition("Flat rate", flat.rate(), null, null, false);
        }
        if (plan.qualifiesForLifeline(units) || (units == 0 && plan.hasLifeline())) {
            return new SlabPosition("Lifeline 0–" + plan.lifelineMaxUnits(), plan.lifelineRate(),
                    plan.lifelineMaxUnits(), plan.slabs().get(0).rate(), true);
        }
        int index = plan.slabIndexFor(units);
        Slab slab = plan.slabs().get(index);
        boolean top = index == plan.slabs().size() - 1;
        return new SlabPosition(plan.slabLabel(index), slab.rate(), slab.uptoUnits(),
                top ? null : plan.slabs().get(index + 1).rate(), false);
    }
}
