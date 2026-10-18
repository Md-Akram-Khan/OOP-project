package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Config;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.NidDao;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.meterphoto.IdCardReader;
import electricity.billing.system.model.NidRecord;
import electricity.billing.system.security.PasswordHasher;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Sign-up for people who need a new meter, in three steps:
 *
 * <ol>
 *   <li>Phone: a one-time code is sent by SMS and must be entered within 5 minutes.</li>
 *   <li>NID: a photo of the card is checked against the dummy NID database.</li>
 *   <li>Account: a username and password, creating an Applicant account.</li>
 * </ol>
 *
 * Each step hands back a short-lived token that the next step needs, so steps
 * can't be skipped. Codes are stored only as hashes and kept in memory; a
 * server restart simply means asking for a new code.
 */
public class SignupService {

    private static final Logger LOG = Logger.getLogger(SignupService.class.getName());
    private static final Pattern BD_MOBILE = Pattern.compile("01[3-9][0-9]{8}");
    private static final SecureRandom RANDOM = new SecureRandom();

    static final int CODE_SECONDS = 5 * 60;
    static final int RESEND_SECONDS = 30;
    static final int MAX_CODES_PER_HOUR = 5;
    static final int MAX_ATTEMPTS = 5;
    private static final long TOKEN_MS = 30 * 60 * 1000L;

    private record PendingCode(String hash, long expires, int[] attempts) {
    }

    private record PhoneToken(String phone, long expires) {
    }

    private record IdentityToken(String phone, String nidNo, long expires) {
    }

    /** A phone verification happening in the browser with Firebase; {@code phoneToken} is set once it succeeds. */
    private static final class BrowserSession {
        final String phone;
        final long expires;
        volatile String phoneToken;

        BrowserSession(String phone, long expires) {
            this.phone = phone;
            this.expires = expires;
        }
    }

    private final SmsGateway sms;
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> sendTimes = new ConcurrentHashMap<>();
    private final Map<String, PhoneToken> phoneTokens = new ConcurrentHashMap<>();
    private final Map<String, IdentityToken> identityTokens = new ConcurrentHashMap<>();
    private final Map<String, BrowserSession> browserSessions = new ConcurrentHashMap<>();
    private final FirebaseConfig firebase;
    private final FirebaseTokens firebaseTokens;

    /** Firebase web settings, from .env. All three must be set to use Firebase Phone Authentication. */
    public record FirebaseConfig(String apiKey, String projectId, String authDomain) {
        static FirebaseConfig fromEnv() {
            String key = Config.get("FIREBASE_API_KEY", "").trim();
            String project = Config.get("FIREBASE_PROJECT_ID", "").trim();
            if (key.isEmpty() || project.isEmpty()) {
                return null;
            }
            return new FirebaseConfig(key, project, Config.get("FIREBASE_AUTH_DOMAIN", project + ".firebaseapp.com").trim());
        }
    }

    public SignupService() {
        this(SmsGateway.fromConfig(), FirebaseConfig.fromEnv());
    }

    public SignupService(SmsGateway sms) {
        this(sms, null);
    }

    public SignupService(SmsGateway sms, FirebaseConfig firebase) {
        this(sms, firebase, firebase == null ? null : new FirebaseTokens(firebase.projectId()));
    }

    /** For tests: a token checker with known keys. */
    SignupService(SmsGateway sms, FirebaseConfig firebase, FirebaseTokens tokens) {
        this.sms = sms;
        this.firebase = firebase;
        this.firebaseTokens = tokens;
    }

    /** "firebase" (code texted by Firebase, checked in the browser), "sms" (our SMS gateway) or "demo" (code shown on screen). */
    public String phoneMode() {
        return firebase != null ? "firebase" : sms.isDemo() ? "demo" : "sms";
    }

    public FirebaseConfig firebase() {
        return firebase;
    }

    // ---- Step 1 with Firebase: verification in the browser -------------------

    /** Starts a browser verification for the phone; the app opens the returned page and then polls. */
    public Api.PhoneSession startBrowserVerification(String typedPhone, String publicUrl) {
        if (firebase == null) {
            throw new AppException("Firebase Phone Authentication isn't set up on the server.");
        }
        String phone = normalizePhone(typedPhone);
        String session = newToken();
        browserSessions.put(session, new BrowserSession(phone, System.currentTimeMillis() + 15 * 60 * 1000L));
        browserSessions.values().removeIf(s -> System.currentTimeMillis() > s.expires);
        return new Api.PhoneSession(session, publicUrl + "/verify-phone?session=" + session, phone);
    }

    /** The phone a browser session is verifying, for the page to show. */
    public String browserSessionPhone(String session) {
        BrowserSession s = browserSessions.get(session == null ? "" : session);
        if (s == null || System.currentTimeMillis() > s.expires) {
            throw new AppException("This verification link has expired. Start again in the app.");
        }
        return s.phone;
    }

    /**
     * The browser finished Firebase's SMS check and sends its ID token. The
     * token must be genuine and for the same phone number the app asked about.
     */
    public void completeBrowserVerification(String session, String idToken) {
        BrowserSession s = browserSessions.get(session == null ? "" : session);
        if (s == null || System.currentTimeMillis() > s.expires) {
            throw new AppException("This verification link has expired. Start again in the app.");
        }
        FirebaseTokens.Verified verified = firebaseTokens.verify(idToken);
        String confirmed = normalizePhone(verified.phoneNumber());
        if (!confirmed.equals(s.phone)) {
            throw new AppException("Firebase verified " + confirmed + ", but the app asked for " + s.phone + ".");
        }
        String token = newToken();
        phoneTokens.put(token, new PhoneToken(s.phone, System.currentTimeMillis() + TOKEN_MS));
        s.phoneToken = token;
    }

    /** Polled by the app: the phone token once the browser verification succeeded, else null. */
    public Api.PhoneVerified browserVerificationResult(String session) {
        BrowserSession s = browserSessions.get(session == null ? "" : session);
        if (s == null || System.currentTimeMillis() > s.expires) {
            throw new AppException("The verification expired. Start again.");
        }
        return s.phoneToken == null ? null : new Api.PhoneVerified(s.phoneToken);
    }

    /** "01712345678", from "+8801712345678", "8801712345678" or "01712-345678". */
    public static String normalizePhone(String typed) {
        String digits = typed == null ? "" : typed.replaceAll("[^0-9]", "");
        if (digits.startsWith("880")) {
            digits = digits.substring(2);
        }
        if (!BD_MOBILE.matcher(digits).matches()) {
            throw new AppException("Enter a Bangladeshi mobile number: 11 digits starting with 01, e.g. 01712345678.");
        }
        return digits;
    }

    // ---- Step 1: phone ------------------------------------------------------

    public Api.OtpSent sendCode(String typedPhone) {
        if (firebase != null) {
            throw new AppException("Phone numbers are verified with Firebase in the browser. Update the app.");
        }
        String phone = normalizePhone(typedPhone);
        long now = System.currentTimeMillis();
        Deque<Long> sent = sendTimes.computeIfAbsent(phone, k -> new ArrayDeque<>());
        synchronized (sent) {
            while (!sent.isEmpty() && now - sent.peekFirst() > 3_600_000L) {
                sent.pollFirst();
            }
            if (!sent.isEmpty() && now - sent.peekLast() < RESEND_SECONDS * 1000L) {
                long wait = RESEND_SECONDS - (now - sent.peekLast()) / 1000;
                throw new AppException("Please wait " + wait + " seconds before asking for another code.");
            }
            if (sent.size() >= MAX_CODES_PER_HOUR) {
                throw new AppException("Too many codes were sent to this number. Try again in an hour.");
            }
            sent.addLast(now);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.put(phone, new PendingCode(hash(phone + ":" + code), now + CODE_SECONDS * 1000L, new int[1]));
        try {
            sms.send(phone, "Your Electricity Billing verification code is " + code + ". It expires in 5 minutes. "
                    + "Don't share it with anyone.");
        } catch (IOException e) {
            codes.remove(phone);
            LOG.warning("SMS to " + phone + " failed: " + e.getMessage());
            throw new AppException("The SMS couldn't be sent. Check the number and try again.");
        }
        return new Api.OtpSent(phone, CODE_SECONDS, RESEND_SECONDS, sms.isDemo() ? code : null);
    }

    public Api.PhoneVerified verifyCode(String typedPhone, String typedCode) {
        String phone = normalizePhone(typedPhone);
        String code = typedCode == null ? "" : typedCode.trim();
        PendingCode pending = codes.get(phone);
        if (pending == null || System.currentTimeMillis() > pending.expires()) {
            codes.remove(phone);
            throw new AppException("That code has expired. Ask for a new one.");
        }
        synchronized (pending) {
            if (pending.attempts()[0] >= MAX_ATTEMPTS) {
                codes.remove(phone);
                throw new AppException("Too many wrong codes. Ask for a new one.");
            }
            if (!MessageDigest.isEqual(pending.hash().getBytes(), hash(phone + ":" + code).getBytes())) {
                pending.attempts()[0]++;
                int left = MAX_ATTEMPTS - pending.attempts()[0];
                throw new AppException(left == 0 ? "Wrong code. Ask for a new one." : "Wrong code. " + left
                        + (left == 1 ? " try" : " tries") + " left.");
            }
        }
        codes.remove(phone);
        String token = newToken();
        phoneTokens.put(token, new PhoneToken(phone, System.currentTimeMillis() + TOKEN_MS));
        return new Api.PhoneVerified(token);
    }

    // ---- Step 2: NID --------------------------------------------------------

    /**
     * Checks the NID card: the front must show one record's NID number, name and
     * date of birth. A photo of the back is required too, but only checked to be
     * a photo other than the front.
     */
    public Api.IdentityVerified verifyNid(String phoneToken, byte[] front, byte[] back) throws SQLException {
        PhoneToken phone = phoneTokens.get(phoneToken == null ? "" : phoneToken);
        if (phone == null || System.currentTimeMillis() > phone.expires()) {
            throw new AppException("Your phone verification has expired. Start again from your phone number.");
        }
        List<NidRecord> records = Database.withConnection(c -> new NidDao(c).all());
        IdCardReader.requireBack(back, front);
        IdCardReader.Result result = IdCardReader.check(front, records);
        NidRecord record = result.record();
        List<Api.CardField> fields = new ArrayList<>();
        // Every detail is shown: the record's exact value where the card matched it, otherwise what was read from the
        // photo. A record's details are never shown unless the card itself matched them, so a stranger's card number
        // can't be used to look up someone's name or date of birth.
        IdCardReader.CardText read = result.read();
        fields.add(new Api.CardField("NID number", result.numberFound() ? record.nidNo() : orUnread(read.nidNo()),
                result.numberFound()));
        fields.add(new Api.CardField("Name", result.nameFound() ? record.nameEn() : orUnread(read.name()), result.nameFound()));
        fields.add(new Api.CardField("Date of birth", result.birthDateFound() ? DAY.format(record.dateOfBirth())
                : orUnread(read.dateOfBirth()), result.birthDateFound()));
        if (!result.verified()) {
            return new Api.IdentityVerified(false, result.problem(), fields, null, phone.phone(), null);
        }
        boolean taken = Database.withConnection(c -> new UserDao(c).nidRegistered(record.nidNo()));
        if (taken) {
            return new Api.IdentityVerified(false, "An account already exists for this NID. Sign in with it instead.", fields,
                    null, phone.phone(), null);
        }
        String token = newToken();
        identityTokens.put(token, new IdentityToken(phone.phone(), record.nidNo(), System.currentTimeMillis() + TOKEN_MS));
        return new Api.IdentityVerified(true, "Identity verified", fields, token, phone.phone(), record);
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static String orUnread(String value) {
        return value == null ? "couldn't be read" : value;
    }

    // ---- Step 3: account ----------------------------------------------------

    /** Creates the Applicant account. The verification tokens are used up. */
    public void createAccount(String identityToken, String username, char[] password) throws SQLException {
        IdentityToken identity = identityTokens.get(identityToken == null ? "" : identityToken);
        if (identity == null || System.currentTimeMillis() > identity.expires()) {
            throw new AppException("Your verification has expired. Start again from your phone number.");
        }
        AuthService.validateCredentials(username, password);
        String hash = PasswordHasher.hash(password);
        Database.inTransaction(c -> {
            NidRecord record = new NidDao(c).find(identity.nidNo())
                    .orElseThrow(() -> new AppException("That NID record no longer exists."));
            UserDao users = new UserDao(c);
            users.lockForAccountCreation();
            if (users.nidRegistered(record.nidNo())) {
                throw new AppException("An account already exists for this NID. Sign in with it instead.");
            }
            if (users.usernameTaken(username)) {
                throw new AppException("That username is already taken.");
            }
            users.insertApplicant(username, record.nameEn(), hash, identity.phone(), record.nidNo());
            return null;
        });
        identityTokens.remove(identityToken);
        phoneTokens.values().removeIf(t -> t.phone().equals(identity.phone()));
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
