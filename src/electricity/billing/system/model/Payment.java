package electricity.billing.system.model;

import java.math.BigDecimal;

/** One online payment attempt for a bill. */
public record Payment(
        String tranId,
        String meterNo,
        String month,
        int year,
        BigDecimal amount,
        String currency,
        Status status) {

    public enum Status {
        /** Checkout page created; the customer hasn't finished paying yet. */
        INITIATED,
        /** Confirmed by the gateway's validation API. */
        PAID,
        FAILED,
        CANCELLED
    }
}
