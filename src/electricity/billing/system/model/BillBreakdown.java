package electricity.billing.system.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every line of a Bangladeshi electricity bill, the way the bill itself prints it:
 * energy priced slab by slab, demand charge on the sanctioned load, VAT, meter
 * rent, and the two totals (by the due date, and after it with the late fee).
 *
 * @param exactTotal      total to the paisa, before rounding
 * @param payable         amount due by the due date, rounded up to whole taka
 * @param payableAfterDue amount due after the due date, rounded up to whole taka
 */
public record BillBreakdown(
        int units,
        boolean lifeline,
        List<Line> lines,
        BigDecimal energy,
        BigDecimal loadKw,
        BigDecimal demandRate,
        BigDecimal demand,
        BigDecimal vatPercent,
        BigDecimal vat,
        BigDecimal meterRent,
        BigDecimal exactTotal,
        BigDecimal payable,
        BigDecimal lateFeePercent,
        BigDecimal lateFee,
        BigDecimal payableAfterDue) {

    /** Units priced at one rate, e.g. "76–200: 125 units × Tk 8.50". */
    public record Line(String label, int units, BigDecimal rate, BigDecimal amount) {
    }

    /** Energy plus demand charge: the base for VAT and the late fee. */
    public BigDecimal subtotal() {
        return energy.add(demand);
    }
}
