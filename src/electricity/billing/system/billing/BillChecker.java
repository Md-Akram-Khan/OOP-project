package electricity.billing.system.billing;

import electricity.billing.system.model.Bill;
import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.BillType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain rules that catch the mistakes made when bills are keyed in by hand,
 * before the bill reaches the customer.
 *
 * The motivating case: a September 2026 Magura bill for 235 units first came
 * out at Tk 5.068 billion because a clerk typed the meter's seal number into
 * the demand-charge field. Any of the load or "times usual" checks below
 * would have stopped it.
 */
public final class BillChecker {

    /** Bills compared against: the meter's most recent ones. */
    private static final int HISTORY = 6;
    private static final int SPIKE_FACTOR = 3;
    private static final BigDecimal HOME_LOAD_LIMIT_KW = new BigDecimal("15");
    private static final int HOME_UNITS_LIMIT = 2000;

    private BillChecker() {
    }

    /**
     * Warnings about a bill that is about to be made. None of them blocks the
     * bill on its own; the admin has to confirm they've looked.
     *
     * @param history    the meter's earlier bills, newest first
     * @param lastOnFile the meter's last recorded reading, or null
     */
    public static List<String> check(BillType type, int units, Integer previousReading, Integer lastOnFile,
            BillBreakdown priced, List<Bill> history) {
        List<String> warnings = new ArrayList<>();
        List<Bill> recent = history.stream().filter(b -> b.units() > 0).limit(HISTORY).toList();
        double average = recent.stream().mapToInt(Bill::units).average().orElse(0);

        if (units == 0) {
            warnings.add("Zero units used. Check the meter was actually read and isn't stuck.");
        }
        if (previousReading != null && lastOnFile != null && !previousReading.equals(lastOnFile)) {
            warnings.add("The previous reading (" + previousReading + ") doesn't match the last bill's reading ("
                    + lastOnFile + "). Was the meter replaced?");
        }
        if (recent.size() >= 2 && units > average * SPIKE_FACTOR) {
            warnings.add(units + " units is " + String.format("%.1f", units / average)
                    + "× this meter's average of " + Math.round(average) + ". Check the reading.");
        }
        if (recent.size() >= 2 && average >= 50 && units > 0 && units < average / 4) {
            warnings.add(units + " units is far below this meter's average of " + Math.round(average)
                    + ". Check for a misread or a faulty meter.");
        }
        if (type == BillType.NORMAL && priced.loadKw().compareTo(HOME_LOAD_LIMIT_KW) > 0) {
            warnings.add("A sanctioned load of " + priced.loadKw().stripTrailingZeros().toPlainString()
                    + " kW is unusual for a home. Check the meter's load before billing the demand charge.");
        }
        if (priced.demand().compareTo(priced.energy()) > 0 && units > 0) {
            warnings.add("The demand charge is larger than the energy charge. Check the sanctioned load.");
        }
        if (type == BillType.NORMAL && units > HOME_UNITS_LIMIT) {
            warnings.add(units + " units is very high for a household. Check the reading.");
        }
        Bill last = history.isEmpty() ? null : history.get(0);
        if (last != null && last.total().compareTo(BigDecimal.valueOf(100)) >= 0
                && priced.payable().compareTo(last.total().multiply(BigDecimal.TEN)) > 0) {
            warnings.add("This bill is more than 10× the previous one (" + last.total().toPlainString() + " taka).");
        }
        return warnings;
    }
}
