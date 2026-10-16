package electricity.billing.system.common;

import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Payment;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.TaxRates;
import electricity.billing.system.model.UserAccount;
import java.math.BigDecimal;

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

    public record NewBillRequest(String meterNo, String month, int year, int units) {
    }

    /** Identifies one bill. {@code meterNo} is ignored when a customer sends it: they can only touch their own. */
    public record BillKey(String meterNo, String month, int year) {
    }

    public record PreviewRequest(String meterNo, int units) {
    }

    /** What a bill would cost, without saving it. */
    public record BillPreview(BillType billType, int units, BigDecimal energyCharge, TaxRates rates, BigDecimal total) {
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
