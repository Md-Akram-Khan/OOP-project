package electricity.billing.system.model;

/** Meter set-up for a customer. */
public record Meter(String meterNo, String meterType, BillType billType, int days) {

    public static final int DEFAULT_BILLING_DAYS = 30;
}
