package electricity.billing.system.common;

import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Complaint;
import electricity.billing.system.model.Notification;
import electricity.billing.system.model.Payment;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.SlabPosition;
import electricity.billing.system.model.UserAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies exchanged between the app and the server, as JSON. */
public final class Api {

    private Api() {
    }

    public record LoginRequest(String username, String password, Role role) {
    }

    public record LoginResponse(String token, UserAccount account) {
    }

    public record RegisterCustomerRequest(String meterNo, String email, String username, String password) {
    }

    public record CreateAdminRequest(String username, String name, String password) {
    }

    /** The fields a customer may change about themselves. */
    public record ContactUpdate(String address, String city, String state, String email, String phone) {
    }

    /**
     * A meter reading to bill. {@code previousReading} may be left null to use the
     * last bill's reading. Bills the checker has concerns about are only saved
     * with {@code acknowledgeWarnings}.
     */
    public record NewBillRequest(String meterNo, String month, int year, Integer previousReading, int currentReading,
            boolean acknowledgeWarnings) {
    }

    /** Identifies one bill. {@code meterNo} is ignored when a customer sends it: they can only touch their own. */
    public record BillKey(String meterNo, String month, int year) {
    }

    public record PreviewRequest(String meterNo, String month, int year, Integer previousReading, int currentReading) {
    }

    /** What a bill would cost, without saving it, and anything the bill checker noticed. */
    public record BillPreview(BillType billType, String planName, int previousReading, int currentReading,
            BillBreakdown breakdown, SlabPosition slab, List<String> warnings, LocalDate dueDate) {
    }

    /** A meter's last recorded reading (null if it has none yet) and how it's billed. */
    public record LastReading(String meterNo, Integer reading, String period, BillType billType, BigDecimal loadKw,
            String planName) {
    }

    public record EstimateRequest(int units) {
    }

    /** A month's bill for a number of units, at the customer's own tariff and load. */
    public record Estimate(String planName, BillBreakdown breakdown, SlabPosition slab) {
    }

    public record ForecastRequest(int currentReading) {
    }

    /**
     * Where this month is heading, from a reading the customer took today.
     *
     * @param dailyBudget units a day that keep the month inside its current slab,
     *                    or null if already in the top slab
     */
    public record Forecast(int lastReading, LocalDate lastReadingDate, int unitsSoFar, int daysSoFar, int cycleDays,
            int projectedUnits, Estimate estimate, Integer dailyBudget) {
    }

    public record NewComplaint(Complaint.Category category, String subject, String details) {
    }

    public record ComplaintUpdate(long id, Complaint.Status status, String reply) {
    }

    public record NotificationFeed(int unread, List<Notification> items) {
    }

    /** Whether online payment is available, and in which mode. */
    public record PaymentConfig(boolean enabled, boolean sandbox, String currency) {
    }

    public record PaymentStartRequest(String month, int year) {
    }

    /** Where to send the customer's browser to pay, and the reference to poll with. */
    public record PaymentStart(String tranId, String gatewayUrl, boolean sandbox) {
    }

    public record PaymentState(String tranId, Payment.Status status, BillStatus billStatus) {
    }

    public record AdminExists(boolean exists) {
    }

    public record ErrorResponse(String error) {
    }
}
