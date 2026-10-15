package electricity.billing.system.server;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.Months;
import electricity.billing.system.model.UserAccount;
import electricity.billing.system.service.AuthService;
import electricity.billing.system.service.BillingService;
import electricity.billing.system.service.CustomerService;

/**
 * Every API endpoint and who may call it.
 *
 * The rule throughout: a customer's identity comes from their session token,
 * never from the request. Customers can only see and change their own meter's
 * data, whatever meter number they send.
 */
final class Endpoints {

    private final AuthService auth = new AuthService();
    private final CustomerService customers = new CustomerService();
    private final BillingService billing = new BillingService();
    private final SessionStore sessions;
    private final LoginThrottle throttle;

    Endpoints(SessionStore sessions, LoginThrottle throttle) {
        this.sessions = sessions;
        this.throttle = throttle;
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
            return null;
        });
        // Anonymous callers may only create the very first admin; AuthService enforces that.
        router.add("POST", "/api/admins", r -> {
            Api.CreateAdminRequest b = r.body(Api.CreateAdminRequest.class);
            auth.createAdmin(text(b.username()), text(b.name()), chars(b.password()), r.user().orElse(null));
            return null;
        });

        // Customers
        router.add("GET", "/api/customers", r -> {
            r.requireAdmin();
            return customers.findAll();
        });
        router.add("POST", "/api/customers", r -> {
            r.requireAdmin();
            Customer draft = r.body(Customer.class);
            return customers.create(trimmed(draft, null, draft.name()));
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
            return changed;
        });

        // Meters
        router.add("GET", "/api/meters/numbers", r -> {
            r.requireAdmin();
            return customers.meterNumbers();
        });
        router.add("PUT", "/api/meters", r -> {
            r.requireAdmin();
            Meter meter = r.body(Meter.class);
            customers.saveMeter(new Meter(text(meter.meterNo()), meter.meterType(), meter.billType(),
                    meter.days() > 0 ? meter.days() : Meter.DEFAULT_BILLING_DAYS));
            return null;
        });

        // Bills
        router.add("POST", "/api/bills", r -> {
            r.requireAdmin();
            Api.NewBillRequest b = r.body(Api.NewBillRequest.class);
            return billing.createBill(text(b.meterNo()), month(b.month()), Request.checkYear(b.year()), b.units());
        });
        router.add("POST", "/api/bills/preview", r -> {
            r.requireAdmin();
            Api.PreviewRequest b = r.body(Api.PreviewRequest.class);
            return billing.preview(text(b.meterNo()), b.units());
        });
        router.add("GET", "/api/bills", r -> {
            UserAccount user = r.requireUser();
            String meter = user.isAdmin() ? r.query("meter") : user.meterNo();
            return billing.search(meter, r.month("month"), r.year("year"));
        });
        router.add("GET", "/api/bills/statement", r -> {
            UserAccount user = r.requireUser();
            String meter = user.isAdmin() ? requireParam(r, "meter") : user.meterNo();
            String month = r.month("month");
            Integer year = r.year("year");
            if (month == null || year == null) {
                throw ApiError.badRequest("Choose a month and year.");
            }
            return billing.statement(meter, month, year);
        });
        router.add("POST", "/api/bills/pay", r -> {
            UserAccount me = r.requireCustomer();
            Api.BillKey b = r.body(Api.BillKey.class);
            billing.submitPayment(me.meterNo(), month(b.month()), Request.checkYear(b.year()));
            return null;
        });
        router.add("POST", "/api/bills/confirm", r -> {
            r.requireAdmin();
            Api.BillKey b = r.body(Api.BillKey.class);
            billing.confirmPayment(text(b.meterNo()), month(b.month()), Request.checkYear(b.year()));
            return null;
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
        return new Api.LoginResponse(sessions.create(account), account);
    }

    private Customer myProfile(UserAccount me) throws Exception {
        return customers.find(me.meterNo())
                .orElseThrow(() -> ApiError.notFound("Your customer details couldn't be found."));
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
