package electricity.billing.system.server;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Money;
import electricity.billing.system.model.Bill;
import electricity.billing.system.model.Complaint;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.model.Months;
import electricity.billing.system.model.UserAccount;
import electricity.billing.system.service.ApplicationService;
import electricity.billing.system.service.AuditService;
import electricity.billing.system.service.AuthService;
import electricity.billing.system.service.BillingService;
import electricity.billing.system.service.ComplaintService;
import electricity.billing.system.service.CustomerService;
import electricity.billing.system.service.NotificationService;
import electricity.billing.system.service.PaymentService;
import electricity.billing.system.service.SignupService;
import java.util.Base64;

/**
 * Every API endpoint and who may call it.
 *
 * The rule throughout: a customer's identity comes from their session token,
 * never from the request. Customers can only see and change their own meter's
 * data, whatever meter number they send.
 *
 * Every change is written to the audit log with the name of whoever made it.
 */
final class Endpoints {

    private final AuthService auth = new AuthService();
    private final CustomerService customers = new CustomerService();
    private final BillingService billing = new BillingService();
    private final ComplaintService complaints = new ComplaintService();
    private final NotificationService notifications = new NotificationService();
    private final AuditService audit = new AuditService();
    private final SignupService signup;
    private final ApplicationService applications = new ApplicationService();
    private final PaymentService payments;
    private final String publicUrl;
    private final SessionStore sessions;
    private final LoginThrottle throttle;

    Endpoints(SessionStore sessions, LoginThrottle throttle, PaymentService payments, SignupService signup, String publicUrl) {
        this.sessions = sessions;
        this.signup = signup;
        this.throttle = throttle;
        this.payments = payments;
        this.publicUrl = publicUrl;
    }

    void register(Router router) {
        router.add("GET", "/api/health", r -> new Health("ok"));

        // Accounts
        router.add("POST", "/api/auth/login", this::login);
        router.add("POST", "/api/auth/logout", r -> {
            r.token().ifPresent(sessions::remove);
            return null;
        });
        router.add("GET", "/api/auth/admin-exists", r -> new Api.AdminExists(auth.adminExists()));
        router.add("POST", "/api/auth/register-customer", r -> {
            Api.RegisterCustomerRequest b = r.body(Api.RegisterCustomerRequest.class);
            auth.registerCustomer(text(b.meterNo()), text(b.email()), text(b.username()), chars(b.password()));
            audit.record(text(b.username()) + " (Customer " + text(b.meterNo()) + ")", "ACCOUNT_CLAIMED", text(b.meterNo()),
                    "Customer created their login");
            return null;
        });
        router.add("GET", "/api/admins", r -> auth.admins(r.requireAdmin()));
        // Anonymous callers may only create the very first admin; AuthService enforces that.
        router.add("POST", "/api/admins", r -> {
            Api.CreateAdminRequest b = r.body(Api.CreateAdminRequest.class);
            UserAccount by = r.user().orElse(null);
            auth.createAdmin(text(b.username()), text(b.name()), chars(b.password()), by);
            audit.record(by == null ? "First-time set-up" : AuditService.actor(by), "ADMIN_CREATED", text(b.username()), text(b.name()));
            return null;
        });

        // New meter sign-up: phone (OTP) -> NID -> applicant account. Each step needs the previous step's token.
        router.add("GET", "/api/signup/phone/mode", r -> new Api.PhoneMode(signup.phoneMode()));
        // Firebase: the app opens the returned page in the browser, then polls the status until Firebase has verified the phone.
        router.add("POST", "/api/signup/phone/start", r -> signup.startBrowserVerification(r.body(Api.OtpRequest.class).phone(), publicUrl));
        router.add("GET", "/api/signup/phone/status", r -> {
            Api.PhoneVerified done = signup.browserVerificationResult(requireParam(r, "session"));
            return new Api.PhoneSessionStatus(done != null, done == null ? null : done.phoneToken());
        });
        router.add("POST", "/api/signup/otp", r -> signup.sendCode(r.body(Api.OtpRequest.class).phone()));
        router.add("POST", "/api/signup/otp/verify", r -> {
            Api.OtpVerify b = r.body(Api.OtpVerify.class);
            return signup.verifyCode(b.phone(), b.code());
        });
        router.add("POST", "/api/signup/nid", r -> {
            Api.NidCheckRequest b = r.body(Api.NidCheckRequest.class, Request.MAX_TWO_PHOTO_BODY_BYTES);
            return signup.verifyNid(b.phoneToken(), photo(b.photo(), "Upload a photo of the front of your NID card."),
                    photo(b.backPhoto(), "Upload a photo of the back of your NID card."));
        });
        router.add("POST", "/api/signup/applicant", r -> {
            Api.ApplicantSignup b = r.body(Api.ApplicantSignup.class);
            signup.createAccount(b.identityToken(), text(b.username()), chars(b.password()));
            audit.record(text(b.username()) + " (Applicant)", "APPLICANT_SIGNED_UP", text(b.username()),
                    "Phone verified by OTP, NID checked against the NID records");
            return null;
        });

        // Meter applications
        router.add("GET", "/api/applicant/me", r -> applications.profile(r.requireApplicant()));
        router.add("GET", "/api/applications", r -> {
            UserAccount user = r.requireUser();
            if (!user.isAdmin()) {
                r.requireApplicant();
            }
            return applications.list(user);
        });
        router.add("POST", "/api/applications", r -> {
            UserAccount me = r.requireApplicant();
            MeterApplication saved = applications.submit(me, r.body(Api.NewApplication.class));
            audit.record(me, "APPLICATION_SUBMITTED", saved.reference(), saved.connection().district() + ", " + saved.categoryLabel()
                    + ", " + saved.loadKw().stripTrailingZeros().toPlainString() + " kW");
            return saved;
        });
        router.add("POST", "/api/applications/update", r -> {
            UserAccount admin = r.requireAdmin();
            MeterApplication saved = applications.update(r.body(Api.ApplicationUpdate.class));
            audit.record(admin, "APPLICATION_UPDATED", saved.reference(), "Status: " + saved.status().label());
            return saved;
        });
        router.add("POST", "/api/applications/connect", r -> {
            UserAccount admin = r.requireAdmin();
            MeterApplication saved = applications.connect(r.body(Api.ConnectMeter.class));
            audit.record(admin, "METER_CONNECTED", saved.reference(), "Meter " + saved.meterNo() + " for " + saved.applicantName()
                    + "; applicant account is now a customer account");
            return saved;
        });

        // Customers
        router.add("GET", "/api/customers", r -> {
            r.requireAdmin();
            return customers.findAll();
        });
        router.add("POST", "/api/customers", r -> {
            UserAccount admin = r.requireAdmin();
            Api.NewCustomerRequest b = r.body(Api.NewCustomerRequest.class);
            Customer draft = trimmed(new Customer(b.meterNo(), b.name(), b.address(), b.city(), b.state(), b.email(), b.phone()),
                    b.meterNo(), b.name());
            Meter settings = b.meterType() == null ? null : new Meter(null, b.meterType(), b.billType(),
                    Meter.DEFAULT_BILLING_DAYS, b.sanctionedLoadKw() == null ? Meter.DEFAULT_LOAD_KW : b.sanctionedLoadKw(), null);
            Customer saved = customers.create(draft, settings);
            audit.record(admin, "CUSTOMER_CREATED", saved.meterNo(), saved.name() + ", " + saved.city()
                    + (settings == null ? "" : "; " + settings.meterType() + ", " + settings.billType().displayName() + ", "
                    + settings.loadKw().stripTrailingZeros().toPlainString() + " kW"));
            return saved;
        });
        router.add("GET", "/api/customers/lookup", r -> {
            r.requireAdmin();
            String meter = requireParam(r, "meter");
            return customers.find(meter).orElseThrow(() -> ApiError.notFound("No customer has meter number " + meter + "."));
        });
        router.add("GET", "/api/customers/me", r -> myProfile(r.requireCustomer()));
        router.add("PUT", "/api/customers/me", r -> {
            UserAccount me = r.requireCustomer();
            Api.ContactUpdate b = r.body(Api.ContactUpdate.class);
            Customer current = myProfile(me);
            Customer changed = trimmed(new Customer(me.meterNo(), current.name(),
                    b.address(), b.city(), b.state(), b.email(), b.phone()), me.meterNo(), current.name());
            customers.updateContact(changed);
            audit.record(me, "CONTACT_UPDATED", me.meterNo(), "Address, email or phone changed");
            return changed;
        });

        // Meters
        router.add("GET", "/api/meters/numbers", r -> {
            r.requireAdmin();
            return customers.meterNumbers();
        });
        router.add("GET", "/api/meters/last-reading", r -> {
            r.requireAdmin();
            return billing.lastReading(requireParam(r, "meter"));
        });
        router.add("PUT", "/api/meters", r -> {
            UserAccount admin = r.requireAdmin();
            Meter m = r.body(Meter.class);
            Meter meter = new Meter(text(m.meterNo()), m.meterType(), m.billType(),
                    m.days() > 0 ? m.days() : Meter.DEFAULT_BILLING_DAYS,
                    m.sanctionedLoadKw() == null ? Meter.DEFAULT_LOAD_KW : m.sanctionedLoadKw(), m.serialNo());
            customers.saveMeter(meter);
            audit.record(admin, "METER_SAVED", meter.meterNo(), meter.meterType() + ", " + meter.billType().displayName()
                    + ", " + meter.loadKw().stripTrailingZeros().toPlainString() + " kW"
                    + (meter.serialNo() == null ? "" : ", serial " + meter.serialNo()));
            return null;
        });

        // Tariffs
        router.add("GET", "/api/tariffs", r -> {
            r.requireUser();
            return billing.tariffs();
        });

        // Bills
        // Bills are made from a photo of the meter; the server reads the reading from it.
        router.add("POST", "/api/bills", r -> {
            UserAccount admin = r.requireAdmin();
            Api.NewBillRequest b = r.body(Api.NewBillRequest.class, Request.MAX_PHOTO_BODY_BYTES);
            Bill bill = billing.createBill(month(b.month()), Request.checkYear(b.year()), b.previousReading(), photo(b.photo()),
                    b.expectedMeterNo(), b.acknowledgeWarnings(), AuditService.actor(admin));
            audit.record(admin, "BILL_CREATED", bill.meterNo() + " " + bill.month() + " " + bill.year(),
                    "Meter identified and read from photo: " + bill.previousReading() + " to " + bill.currentReading() + ", " + bill.units()
                            + " units, " + Money.tk(bill.total()) + (bill.hasWarnings() ? "; saved despite "
                            + bill.warnings().size() + " checker warning(s)" : ""));
            return bill;
        });
        router.add("POST", "/api/bills/preview", r -> {
            r.requireAdmin();
            Api.PreviewRequest b = r.body(Api.PreviewRequest.class, Request.MAX_PHOTO_BODY_BYTES);
            return billing.preview(month(b.month()), Request.checkYear(b.year()), b.previousReading(), photo(b.photo()));
        });
        router.add("GET", "/api/bills/photo", r -> {
            UserAccount user = r.requireAdminOrCustomer();
            String meter = user.isAdmin() ? requireParam(r, "meter") : user.meterNo();
            String month = r.month("month");
            Integer year = r.year("year");
            if (month == null || year == null) {
                throw ApiError.badRequest("Choose a month and year.");
            }
            return billing.photo(meter, month, year);
        });
        router.add("GET", "/api/bills", r -> {
            UserAccount user = r.requireAdminOrCustomer();
            String meter = user.isAdmin() ? r.query("meter") : user.meterNo();
            return billing.search(meter, r.month("month"), r.year("year"));
        });
        router.add("GET", "/api/bills/statement", r -> {
            UserAccount user = r.requireAdminOrCustomer();
            String meter = user.isAdmin() ? requireParam(r, "meter") : user.meterNo();
            String month = r.month("month");
            Integer year = r.year("year");
            if (month == null || year == null) {
                throw ApiError.badRequest("Choose a month and year.");
            }
            return billing.statement(meter, month, year);
        });
        // Usage planning (customers, for their own meter)
        router.add("POST", "/api/usage/estimate", r -> {
            UserAccount me = r.requireCustomer();
            return billing.estimate(me.meterNo(), r.body(Api.EstimateRequest.class).units());
        });
        router.add("POST", "/api/usage/forecast", r -> {
            UserAccount me = r.requireCustomer();
            return billing.forecast(me.meterNo(), r.body(Api.ForecastRequest.class).currentReading());
        });

        // Complaints
        router.add("GET", "/api/complaints", r -> {
            UserAccount user = r.requireAdminOrCustomer();
            return complaints.list(user.isAdmin() ? null : user.meterNo());
        });
        router.add("POST", "/api/complaints", r -> {
            UserAccount me = r.requireCustomer();
            Complaint saved = complaints.open(me.meterNo(), r.body(Api.NewComplaint.class));
            audit.record(me, "COMPLAINT_OPENED", saved.ticket(), saved.category().label() + ": " + saved.subject());
            return saved;
        });
        router.add("POST", "/api/complaints/update", r -> {
            UserAccount admin = r.requireAdmin();
            Complaint saved = complaints.update(r.body(Api.ComplaintUpdate.class));
            audit.record(admin, "COMPLAINT_UPDATED", saved.ticket(), "Status: " + saved.status().label());
            return saved;
        });

        // Notifications (customers)
        router.add("GET", "/api/notifications", r -> notifications.feed(r.requireCustomer().meterNo()));
        router.add("POST", "/api/notifications/read", r -> {
            notifications.markAllRead(r.requireCustomer().meterNo());
            return null;
        });

        // Audit log (admins)
        router.add("GET", "/api/audit", r -> {
            r.requireAdmin();
            String limit = r.query("limit");
            try {
                return audit.recent(limit == null ? 200 : Integer.parseInt(limit));
            } catch (NumberFormatException e) {
                throw ApiError.badRequest("Limit must be a number.");
            }
        });

        // Online payments (SSLCommerz). The amount always comes from the bill on the server.
        router.add("GET", "/api/payments/config", r -> {
            r.requireUser();
            return payments.config();
        });
        router.add("POST", "/api/payments/start", r -> {
            UserAccount me = r.requireCustomer();
            Api.PaymentStartRequest b = r.body(Api.PaymentStartRequest.class);
            return payments.start(me.meterNo(), month(b.month()), Request.checkYear(b.year()), publicUrl);
        });
        router.add("GET", "/api/payments/status", r -> {
            UserAccount me = r.requireCustomer();
            return payments.status(requireParam(r, "tran"), me.meterNo());
        });
    }

    private record Health(String status) {
    }

    private Object login(Request r) throws Exception {
        Api.LoginRequest b = r.body(Api.LoginRequest.class);
        if (b.role() == null) {
            throw ApiError.badRequest("Choose whether you're logging in as Admin or Customer.");
        }
        String username = text(b.username());
        throttle.checkAllowed(username);
        UserAccount account;
        try {
            account = auth.login(username, chars(b.password()), b.role());
        } catch (AppException e) {
            throttle.recordFailure(username);
            throw e;
        }
        throttle.recordSuccess(username);
        if (account.isAdmin()) {
            audit.record(account, "ADMIN_LOGIN", account.username(), null);
        }
        return new Api.LoginResponse(sessions.create(account), account);
    }

    private Customer myProfile(UserAccount me) throws Exception {
        return customers.find(me.meterNo())
                .orElseThrow(() -> ApiError.notFound("Your customer details couldn't be found."));
    }

    /** A base64 photo from the app, as bytes. */
    private static byte[] photo(String base64) {
        return photo(base64, "Upload a photo of the meter.");
    }

    private static byte[] photo(String base64, String missing) {
        if (base64 == null || base64.isBlank()) {
            throw ApiError.badRequest(missing);
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw ApiError.badRequest("The photo couldn't be read.");
        }
    }

    private static String requireParam(Request r, String name) {
        String value = r.query(name);
        if (value == null) {
            throw ApiError.badRequest("Missing '" + name + "'.");
        }
        return value;
    }

    private static String month(String month) {
        if (!Months.isValid(month)) {
            throw ApiError.badRequest("Unknown month: " + month);
        }
        return month;
    }

    private static Customer trimmed(Customer c, String meterNo, String name) {
        return new Customer(meterNo, text(name), text(c.address()), text(c.city()),
                text(c.state()), text(c.email()), text(c.phone()));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }
}
