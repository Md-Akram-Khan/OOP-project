package electricity.billing.system.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Why this bill differs from the previous one, in taka. The amounts of the
 * reasons add up to the difference between the two totals.
 */
public record BillExplanation(
        String comparedWith,
        BigDecimal previousTotal,
        BigDecimal currentTotal,
        List<Reason> reasons) {

    /** One cause of the change. {@code amount} is null for notes that are already counted in another reason. */
    public record Reason(String title, String detail, BigDecimal amount) {
    }

    public BigDecimal difference() {
        return currentTotal.subtract(previousTotal);
    }
}
