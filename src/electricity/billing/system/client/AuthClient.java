package electricity.billing.system.client;

import com.google.gson.reflect.TypeToken;
import electricity.billing.system.common.Api;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.UserAccount;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Logging in and out, and creating accounts. */
public class AuthClient {

    private static final Type ADMIN_LIST = new TypeToken<List<Api.AdminInfo>>() { }.getType();

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

    // ---- New meter sign-up --------------------------------------------------

    /** How the server verifies phone numbers: "firebase", "sms" or "demo". */
    public String phoneMode() {
        Api.PhoneMode mode = api.get("/api/signup/phone/mode", Api.PhoneMode.class);
        return mode.mode();
    }

    /** Firebase: starts a verification to finish in the browser. */
    public Api.PhoneSession startPhoneVerification(String phone) {
        return api.post("/api/signup/phone/start", new Api.OtpRequest(phone), Api.PhoneSession.class);
    }

    public Api.PhoneSessionStatus phoneVerificationStatus(String session) {
        return api.get(ApiClient.withQuery("/api/signup/phone/status", ApiClient.params("session", session)),
                Api.PhoneSessionStatus.class);
    }

    /** Sends a one-time code to the phone. In demo mode the code comes back to show on screen. */
    public Api.OtpSent sendOtp(String phone) {
        return api.post("/api/signup/otp", new Api.OtpRequest(phone), Api.OtpSent.class);
    }

    public Api.PhoneVerified verifyOtp(String phone, String code) {
        return api.post("/api/signup/otp/verify", new Api.OtpVerify(phone, code), Api.PhoneVerified.class);
    }

    /** Sends photos of both sides of the NID card; the front (with the number) is checked against the NID records. */
    public Api.IdentityVerified verifyNid(String phoneToken, byte[] front, byte[] back) {
        Base64.Encoder base64 = Base64.getEncoder();
        return api.post("/api/signup/nid", new Api.NidCheckRequest(phoneToken, base64.encodeToString(front),
                base64.encodeToString(back)), Api.IdentityVerified.class);
    }

    public void createApplicant(String identityToken, String username, char[] password) {
        api.post("/api/signup/applicant", new Api.ApplicantSignup(identityToken, username, new String(password)), null);
    }

    /** Admin: every administrator, with their last sign-in. */
    public List<Api.AdminInfo> admins() {
        return api.get("/api/admins", ADMIN_LIST);
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
