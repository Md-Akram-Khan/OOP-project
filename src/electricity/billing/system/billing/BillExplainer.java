package electricity.billing.system.billing;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.BillExplanation;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.TariffPlan;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Answers the question customers ask most: "why is my bill different this month?"
 *
 * The change is split into two steps that add up exactly:
 * <ol>
 *   <li>price change: last month's units priced with this month's tariff and
 *       load, compared with what last month actually cost;</li>
 *   <li>usage change: this month's units against last month's, both at this
 *       month's prices.</li>
 * </ol>
 * Notes about slabs (crossing into a dearer band, losing the lifeline rate)
 * explain the usage step and carry no amount of their own.
 */
public final class BillExplainer {

    private BillExplainer() {
    }

    /** Null when there's no earlier bill to compare with. */
    public static BillExplanation explain(Bill current, Bill previous, TariffPlan plan, BigDecimal loadKw) {
        if (current == null || previous == null || current.isLegacy()) {
            return null;
        }
        BillBreakdown now = BillCalculator.calculate(current.units(), loadKw, plan);
        BillBreakdown sameUnits = BillCalculator.calculate(previous.units(), loadKw, plan);
        List<BillExplanation.Reason> reasons = new ArrayList<>();

        BigDecimal priceEffect = sameUnits.payable().subtract(previous.total());
        if (priceEffect.abs().compareTo(BigDecimal.ONE) >= 0) {
            reasons.add(priceReason(previous, plan, loadKw, priceEffect));
        }

        int moreUnits = current.units() - previous.units();
        BigDecimal usageEffect = current.total().subtract(sameUnits.payable());
        if (moreUnits != 0) {
            reasons.add(new BillExplanation.Reason(
                    moreUnits > 0 ? "You used " + moreUnits + " more units" : "You used " + -moreUnits + " fewer units",
                    previous.units() + " units last time, " + current.units() + " units this time.",
                    usageEffect));
        } else if (usageEffect.signum() != 0) {
            reasons.add(new BillExplanation.Reason("Rounding", "Bills are rounded up to whole taka.", usageEffect));
        }

        if (sameUnits.lifeline() && !now.lifeline()) {
            reasons.add(new BillExplanation.Reason("You lost the lifeline rate",
                    "Homes using " + plan.lifelineMaxUnits() + " units or fewer pay " + plan.lifelineRate().toPlainString()
                            + " taka for every unit. Above that, all units move to the normal slabs.", null));
        } else if (!now.lifeline() && previous.units() > 0) {
            int before = plan.slabIndexFor(previous.units());
            int after = plan.slabIndexFor(current.units());
            if (after > before && plan.billType() == BillType.NORMAL) {
                BillBreakdown.Line top = now.lines().get(now.lines().size() - 1);
                reasons.add(new BillExplanation.Reason("You moved into the " + plan.slabLabel(after) + " slab",
                        top.units() + " units were charged at " + top.rate().toPlainString() + " taka each, against "
                                + plan.slabs().get(before).rate().toPlainString() + " taka in your previous top slab.", null));
            }
        }
        if (reasons.isEmpty()) {
            reasons.add(new BillExplanation.Reason("No change", "Same usage and the same prices as last time.", BigDecimal.ZERO));
        }
        return new BillExplanation(previous.month() + " " + previous.year(), previous.total(), current.total(), reasons);
    }

    private static BillExplanation.Reason priceReason(Bill previous, TariffPlan plan, BigDecimal loadKw, BigDecimal amount) {
        String sameUnits = "The same " + previous.units() + " units would cost "
                + (amount.signum() > 0 ? "more" : "less") + " at this month's prices.";
        if (previous.isLegacy()) {
            return new BillExplanation.Reason("New slab tariff",
                    "Your previous bill used the old flat-rate formula. " + sameUnits, amount);
        }
        if (previous.charges().tariffPlanId() != plan.id()) {
            return new BillExplanation.Reason("Tariff changed", "This bill uses " + plan.name() + ". " + sameUnits, amount);
        }
        if (!Objects.equals(previous.charges().loadKw().stripTrailingZeros(), loadKw.stripTrailingZeros())) {
            return new BillExplanation.Reason("Sanctioned load changed",
                    "From " + previous.charges().loadKw().stripTrailingZeros().toPlainString() + " kW to "
                            + loadKw.stripTrailingZeros().toPlainString() + " kW, which changes the demand charge.", amount);
        }
        return new BillExplanation.Reason("Other charges", sameUnits, amount);
    }
}
