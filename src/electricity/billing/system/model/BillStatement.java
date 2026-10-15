package electricity.billing.system.model;

/**
 * Everything needed to print a bill. {@code meter} and {@code bill} are null
 * when the meter has not been set up or no bill exists for the requested month.
 */
public record BillStatement(Customer customer, Meter meter, TaxRates rates, Bill bill) {
}
