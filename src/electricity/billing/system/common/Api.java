package electricity.billing.system.common;

import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;

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

    public record AdminExists(boolean exists) {
    }

    public record ErrorResponse(String error) {
    }
}
