package electricity.billing.system.model;

/**
 * Everything needed to print a bill. {@code meter} and {@code bill} are null
 * when the meter has not been set up or no bill exists for the requested month.
 * {@code breakdown} is null for bills made before the slab tariff, and
 * {@code explanation} is null when there's no earlier bill to compare with.
 */
public record BillStatement(
        Customer customer,
        Meter meter,
        TariffPlan plan,
        Bill bill,
        BillBreakdown breakdown,
        BillExplanation explanation) {
}
