package electricity.billing.system.client;

import electricity.billing.system.common.Api;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import java.util.concurrent.CompletableFuture;

/** Logging in and out, and creating accounts. */
public class AuthClient {

    private final ApiClient api = ApiClient.get();

    public UserAccount login(String username, char[] password, Role role) {
        Api.LoginResponse response = api.post("/api/auth/login",
                new Api.LoginRequest(username.trim(), new String(password), role), Api.LoginResponse.class);
        api.startSession(response.token(), response.account());
        return response.account();
    }

    /** Ends the session on the server in the background; the app doesn't wait for it. */
    public void logout() {
        CompletableFuture.runAsync(() -> {
            try {
                api.post("/api/auth/logout", null, null);
            } catch (RuntimeException ignored) {
                // The token expires on its own anyway.
            }
        });
        api.endSession();
    }

    public boolean adminExists() {
        Api.AdminExists response = api.get("/api/auth/admin-exists", Api.AdminExists.class);
        return response.exists();
    }

    public void registerCustomer(String meterNo, String email, String username, char[] password) {
        api.post("/api/auth/register-customer",
                new Api.RegisterCustomerRequest(meterNo, email, username, new String(password)), null);
    }

    /** As a logged-in admin, adds another admin; with nobody logged in, creates the first admin. */
    public void createAdmin(String username, String name, char[] password) {
        api.post("/api/admins", new Api.CreateAdminRequest(username, name, new String(password)), null);
    }
}
