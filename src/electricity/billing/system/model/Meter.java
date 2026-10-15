package electricity.billing.system.model;

import java.util.List;

/** Meter set-up for a customer. */
public record Meter(String meterNo, String meterType, BillType billType, int days) {

    public static final int DEFAULT_BILLING_DAYS = 30;
    public static final List<String> TYPES = List.of("Electric Meter", "Solar Meter", "Smart Meter");
}
