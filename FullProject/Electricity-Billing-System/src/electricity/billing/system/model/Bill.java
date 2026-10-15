package electricity.billing.system.model;

import java.math.BigDecimal;

/** One month's bill for one meter. A meter has at most one bill per month and year. */
public record Bill(
        String meterNo,
        String month,
        int year,
        int units,
        BigDecimal total,
        BillStatus status) {
}
