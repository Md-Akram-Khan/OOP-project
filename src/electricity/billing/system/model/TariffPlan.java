package electricity.billing.system.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A tariff as published by the regulator (BERC), for one customer category,
 * in force from {@code effectiveFrom}. Rates live in the database, never in
 * code, because BERC revises them every year or two.
 *
 * @param lifelineMaxUnits households using at most this many units in a month
 *                         pay {@code lifelineRate} for every unit; null if the
 *                         category has no lifeline band
 * @param demandRatePerKw  monthly demand charge per kW of sanctioned load
 * @param vatPercent       VAT on energy plus demand charge
 * @param lateFeePercent   surcharge on energy plus demand charge after the due date
 * @param dueDays          days from the bill date to the due date
 */
public record TariffPlan(
        long id,
        BillType billType,
        String name,
        LocalDate effectiveFrom,
        List<Slab> slabs,
        Integer lifelineMaxUnits,
        BigDecimal lifelineRate,
        BigDecimal demandRatePerKw,
        BigDecimal vatPercent,
        BigDecimal lateFeePercent,
        BigDecimal meterRent,
        int dueDays,
        String source) {

    public boolean hasLifeline() {
        return lifelineMaxUnits != null && lifelineRate != null;
    }

    /** First unit of slab {@code index}, e.g. 76 for the 76–200 band. */
    public int slabStart(int index) {
        return index == 0 ? 0 : slabs.get(index - 1).uptoUnits() + 1;
    }

    /** "0–75", "76–200" or "Over 600". */
    public String slabLabel(int index) {
        Integer upto = slabs.get(index).uptoUnits();
        if (upto == null) {
            return index == 0 ? "All units" : "Over " + slabs.get(index - 1).uptoUnits();
        }
        return slabStart(index) + "–" + upto;
    }

    /** The slab that a month's last unit falls in. */
    public int slabIndexFor(int units) {
        for (int i = 0; i < slabs.size(); i++) {
            Integer upto = slabs.get(i).uptoUnits();
            if (upto == null || units <= upto) {
                return i;
            }
        }
        return slabs.size() - 1;
    }

    public boolean qualifiesForLifeline(int units) {
        return hasLifeline() && units > 0 && units <= lifelineMaxUnits;
    }
}
