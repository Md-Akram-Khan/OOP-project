package electricity.billing.system.common;

import electricity.billing.system.model.BillBreakdown;
import electricity.billing.system.model.BillStatus;
import electricity.billing.system.model.BillType;
import electricity.billing.system.model.Complaint;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.model.NidRecord;
import electricity.billing.system.model.Notification;
import electricity.billing.system.model.Payment;
import electricity.billing.system.model.PhotoReading;
import electricity.billing.system.model.Role;
import electricity.billing.system.model.SlabPosition;
import electricity.billing.system.model.UserAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * A new customer and, optionally, their meter. The fields match Customer, so
     * a plain customer body still works; with {@code meterType} set, the
     * customer and meter are saved together or not at all.
     */
    public record NewCustomerRequest(String meterNo, String name, String address, String city, String state, String email,
            String phone, String meterType, BillType billType, BigDecimal sanctionedLoadKw) {
    }

    /** The fields a customer may change about themselves. */
    public record ContactUpdate(String address, String city, String state, String email, String phone) {
    }

    /**
     * A bill to make from a photo of the meter ({@code photo}, base64 JPEG or PNG).
     * The server works out which meter it is from the number printed on it and
     * reads the current reading off the display; nobody types either.
     * {@code expectedMeterNo} is the meter the admin was shown in the preview: if
     * the server identifies a different one, nothing is saved.
     * {@code previousReading} is only used for a meter's first bill, when no
     * reading is on file. Bills the checker has concerns about are only saved
     * with {@code acknowledgeWarnings}.
     */
    public record NewBillRequest(String month, int year, Integer previousReading, String photo, String expectedMeterNo,
            boolean acknowledgeWarnings) {
    }

    /** Identifies one bill. {@code meterNo} is ignored when a customer sends it: they can only touch their own. */
    public record BillKey(String meterNo, String month, int year) {
    }

    public record PreviewRequest(String month, int year, Integer previousReading, String photo) {
    }

    /**
     * What a photo shows and what the bill would cost, without saving anything.
     * When the meter has no reading on file and none was given,
     * {@code needsPreviousReading} is true and there's no price yet.
     */
    public record BillPreview(String meterNo, String customerName, BillType billType, BigDecimal loadKw, String planName,
            Integer previousReading, String previousPeriod, boolean needsPreviousReading, int currentReading,
            PhotoReading photo, BillBreakdown breakdown, SlabPosition slab, List<String> warnings, LocalDate dueDate) {
    }

    /** A meter's last recorded reading (null if it has none yet), its serial and how it's billed. */
    public record LastReading(String meterNo, Integer reading, String period, BillType billType, BigDecimal loadKw,
            String planName, String meterType, String serialNo) {
    }

    /** The photo a bill was read from (base64), with what was read and who took it. */
    public record MeterPhoto(String image, String contentType, String displayText, String uploadedBy, LocalDateTime uploadedAt) {
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

    /** One administrator, as listed on the Team page. Never carries the password hash. */
    public record AdminInfo(String username, String name, LocalDateTime lastSignIn, boolean you) {
    }

    // ---- New meter sign-up: phone (OTP), then NID, then an applicant account ----

    public record OtpRequest(String phone) {
    }

    /**
     * A code was sent. {@code demoCode} is filled only in demo mode (no SMS
     * gateway configured), so the app can show the code; it's null when a real
     * SMS was sent.
     */
    public record OtpSent(String phone, int expiresInSeconds, int resendInSeconds, String demoCode) {
    }

    public record OtpVerify(String phone, String code) {
    }

    /** Proof the phone was verified, used for the next step. Expires after 30 minutes. */
    public record PhoneVerified(String phoneToken) {
    }

    /** Photos of both sides of the NID card, as base64: the front (with the NID number) is checked; the back is required. */
    public record NidCheckRequest(String phoneToken, String photo, String backPhoto) {
    }

    /** One detail read from the NID card: shown so the applicant sees what was extracted. */
    public record CardField(String label, String value, boolean matched) {
    }

    /**
     * The outcome of checking an NID card photo. When {@code verified}, the
     * record and the proof for creating the account are included; otherwise
     * {@code message} says what went wrong and {@code fields} shows what was and
     * wasn't found, without revealing the stored record's details.
     */
    public record IdentityVerified(boolean verified, String message, List<CardField> fields, String identityToken, String phone,
            NidRecord record) {
    }

    /** A phone verification to finish in the browser (Firebase). The app opens {@code url}, then polls with {@code session}. */
    public record PhoneSession(String session, String url, String phone) {
    }

    /** How phone numbers are verified: "firebase", "sms" or "demo". */
    public record PhoneMode(String mode) {
    }

    public record PhoneSessionStatus(boolean verified, String phoneToken) {
    }

    public record ApplicantSignup(String identityToken, String username, String password) {
    }

    /** The signed-in applicant's verified details. */
    public record ApplicantProfile(String username, String name, String nidNo, String phone) {
    }

    /** What an applicant fills in to apply for a meter; name, NID and phone come from verification. */
    public record NewApplication(String email, String house, String road, String area, String postOffice, String district,
            String division, BillType billType, BigDecimal loadKw, MeterApplication.Phase phase,
            MeterApplication.Ownership ownership, String notes) {
    }

    public record ApplicationUpdate(long id, MeterApplication.Status status, String note) {
    }

    /** The meter installed for an approved application: its printed number and type. */
    public record ConnectMeter(long id, String meterNo, String meterType) {
    }

    public record AdminExists(boolean exists) {
    }

    public record ErrorResponse(String error) {
    }
}
