package electricity.billing.system.billing;

import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.TariffPlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Prices a month the way a Bangladeshi postpaid bill does:
 *
 * <ol>
 *   <li>energy charge, from the category's {@link Tariff};</li>
 *   <li>demand charge = sanctioned load (kW) × the monthly rate per kW;</li>
 *   <li>VAT on energy plus demand charge;</li>
 *   <li>meter rent;</li>
 *   <li>the total, rounded up to whole taka, and a second "after the due date"
 *       total that adds the late-payment surcharge on energy plus demand.</li>
 * </ol>
 *
 * Checked against a real September 2026 Magura Palli Bidyut bill: 235 units on
 * a 2 kW connection comes to Tk 1,963, or Tk 2,056 after the deadline.
 */
public final class BillCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BillCalculator() {
    }

    public static BillBreakdown calculate(int units, BigDecimal loadKw, TariffPlan plan) {
        if (units < 0) {
            throw new IllegalArgumentException("Units consumed cannot be negative.");
        }
        Tariff tariff = Tariff.forBillType(plan.billType());
        List<BillBreakdown.Line> lines = tariff.energyLines(units, plan);
        BigDecimal energy = lines.stream().map(BillBreakdown.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal demand = loadKw.multiply(plan.demandRatePerKw()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = energy.add(demand);
        BigDecimal vat = percent(subtotal, plan.vatPercent());
        BigDecimal exact = subtotal.add(vat).add(plan.meterRent()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lateFee = percent(subtotal, plan.lateFeePercent());
        return new BillBreakdown(units, plan.qualifiesForLifeline(units), lines, energy, loadKw, plan.demandRatePerKw(),
                demand, plan.vatPercent(), vat, plan.meterRent(), exact, wholeTaka(exact),
                plan.lateFeePercent(), lateFee, wholeTaka(exact.add(lateFee)));
    }

    /** Bills are payable in whole taka, rounded up, as the printed bills show. */
    static BigDecimal wholeTaka(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.CEILING).setScale(2);
    }

    private static BigDecimal percent(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
