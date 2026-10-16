package electricity.billing.system.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One month's bill for one meter. A meter has at most one bill per month and year.
 *
 * Bills made before the slab tariff was introduced have no readings, charges
 * or due date ({@link #isLegacy()}); they keep the total they were issued with.
 *
 * @param total         amount due by the due date
 * @param totalAfterDue amount due after the due date, including the late fee
 * @param issuedOn      the day the meter was read and the bill made
 * @param warnings      what the bill checker flagged when the bill was made
 */
public record Bill(
        String meterNo,
        String month,
        int year,
        int units,
        BigDecimal total,
        BillStatus status,
        Integer previousReading,
        Integer currentReading,
        BillCharges charges,
        BigDecimal totalAfterDue,
        LocalDate dueDate,
        LocalDate issuedOn,
        List<String> warnings) {

    /** A bill from before the slab tariff: only units and a total. */
    public static Bill legacy(String meterNo, String month, int year, int units, BigDecimal total, BillStatus status) {
        return new Bill(meterNo, month, year, units, total, status, null, null, null, null, null, null, List.of());
    }

    public boolean isLegacy() {
        return charges == null;
    }

    /** Unpaid and past its due date. A bill under review is not overdue: the customer has paid. */
    public boolean isOverdue(LocalDate today) {
        return status == BillStatus.NOT_PAID && dueDate != null && today.isAfter(dueDate);
    }

    /** What the customer owes today: the after-due-date total once the due date has passed. */
    public BigDecimal amountDue(LocalDate today) {
        return isOverdue(today) && totalAfterDue != null ? totalAfterDue : total;
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
