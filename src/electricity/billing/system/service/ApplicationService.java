package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Validation;
import electricity.billing.system.db.ApplicationDao;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.model.UserAccount;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Applications for a new meter: applicants submit them, admins review them,
 * and once the meter is installed, connecting it creates the customer.
 */
public class ApplicationService {

    /** Bangladesh's eight divisions. */
    public static final List<String> DIVISIONS = List.of("Barishal", "Chattogram", "Dhaka", "Khulna", "Mymensingh",
            "Rajshahi", "Rangpur", "Sylhet");

    public Api.ApplicantProfile profile(UserAccount user) throws SQLException {
        UserDao.Applicant a = applicant(user);
        return new Api.ApplicantProfile(a.username(), a.name(), a.nidNo(), a.phone());
    }

    /** An applicant applies; name, NID and phone come from their verification, not the form. */
    public MeterApplication submit(UserAccount user, Api.NewApplication form) throws SQLException {
        UserDao.Applicant who = applicant(user);
        String email = text(form.email());
        Validation.customer(new Customer("X", who.name(), "x", "x", "x", email, who.phone()));
        require("House / holding", form.house(), 60);
        require("Road / village", form.road(), 80);
        require("Area", form.area(), 60);
        require("Post office", form.postOffice(), 60);
        require("District", form.district(), 30);
        if (!DIVISIONS.contains(text(form.division()))) {
            throw new AppException("Choose a division.");
        }
        if (form.billType() == null || form.phase() == null || form.ownership() == null) {
            throw new AppException("Choose the connection category, phase and whether you own the premises.");
        }
        BigDecimal load = form.loadKw();
        if (load == null || load.signum() <= 0 || load.compareTo(Meter.MAX_LOAD_KW) > 0) {
            throw new AppException("Enter the load you need in kW, e.g. 2.");
        }
        String notes = text(form.notes());
        if (notes.length() > 500) {
            throw new AppException("Keep the notes under 500 characters.");
        }
        MeterApplication draft = new MeterApplication(0, who.username(), who.name(), who.nidNo(), who.phone(), email,
                text(form.house()), text(form.road()), text(form.area()), text(form.postOffice()), text(form.district()),
                text(form.division()), form.billType(), load, form.phase(), form.ownership(), notes.isEmpty() ? null : notes,
                MeterApplication.Status.SUBMITTED, null, null, null, null);
        return Database.inTransaction(c -> {
            ApplicationDao applications = new ApplicationDao(c);
            if (applications.list(who.id()).stream().anyMatch(a -> a.status().isOpen())) {
                throw new AppException("You already have an application in progress. You can apply again once it's decided.");
            }
            long id = applications.insert(who.id(), draft);
            return applications.find(id).orElseThrow();
        });
    }

    /** An applicant's own applications, or everyone's for an admin. */
    public List<MeterApplication> list(UserAccount user) throws SQLException {
        if (user.isAdmin()) {
            return Database.withConnection(c -> new ApplicationDao(c).list(null));
        }
        UserDao.Applicant who = applicant(user);
        return Database.withConnection(c -> new ApplicationDao(c).list(who.id()));
    }

    /** Admin: move an application along (under review, approved, rejected) with a note to the applicant. */
    public MeterApplication update(Api.ApplicationUpdate request) throws SQLException {
        if (request.status() == null || request.status() == MeterApplication.Status.CONNECTED) {
            throw new AppException(request.status() == null ? "Choose a status."
                    : "Use Connect meter once the meter is installed.");
        }
        String note = text(request.note());
        if (note.length() > 500) {
            throw new AppException("Keep the note under 500 characters.");
        }
        if (request.status() == MeterApplication.Status.REJECTED && note.isEmpty()) {
            throw new AppException("Tell the applicant why the application was rejected.");
        }
        return Database.inTransaction(c -> {
            ApplicationDao applications = new ApplicationDao(c);
            MeterApplication current = applications.find(request.id())
                    .orElseThrow(() -> new AppException("That application doesn't exist."));
            if (current.status() == MeterApplication.Status.CONNECTED) {
                throw new AppException("This application's meter is already connected.");
            }
            applications.update(current.id(), request.status(), note.isEmpty() ? current.adminNote() : note, null);
            return applications.find(current.id()).orElseThrow();
        });
    }

    /**
     * Admin: the meter for an approved application has been installed. Creates
     * the customer under the meter's printed number, sets up the meter, and
     * turns the applicant's login into that customer's login, all at once.
     */
    public MeterApplication connect(Api.ConnectMeter request) throws SQLException {
        String meterNo = Validation.meterNumber(request.meterNo());
        String type = request.meterType() == null ? Meter.TYPES.get(0) : request.meterType();
        if (!Meter.TYPES.contains(type)) {
            throw new AppException("Choose a meter type.");
        }
        try {
            return Database.inTransaction(c -> {
                ApplicationDao applications = new ApplicationDao(c);
                long loginId = applications.loginId(request.id());
                MeterApplication app = applications.find(request.id()).orElseThrow();
                if (app.status() != MeterApplication.Status.APPROVED) {
                    throw new AppException("Only approved applications can be connected. Approve it first.");
                }
                CustomerDao customers = new CustomerDao(c);
                MeterDao meters = new MeterDao(c);
                String owner = meters.serialOwner(meterNo);
                if (customers.exists(meterNo) || owner != null) {
                    throw new AppException("Meter number " + meterNo + " is already registered to another customer.");
                }
                String address = app.address();
                Customer customer = new Customer(meterNo, app.applicantName(), address.length() > 100 ? address.substring(0, 100) : address,
                        app.district(), app.division(), app.email(), app.phone());
                Validation.customer(customer);
                customers.insert(customer);
                meters.save(new Meter(meterNo, type, app.billType(), Meter.DEFAULT_BILLING_DAYS, app.loadKw(), meterNo));
                if (new UserDao(c).makeCustomer(loginId, meterNo) != 1) {
                    throw new AppException("The applicant's account couldn't be linked to the meter.");
                }
                applications.update(app.id(), MeterApplication.Status.CONNECTED,
                        "Meter " + meterNo + " connected. Sign in again to see your bills.", meterNo);
                return applications.find(app.id()).orElseThrow();
            });
        } catch (SQLException e) {
            if (Database.isUniqueViolation(e)) {
                throw new AppException("Meter number " + meterNo + " is already registered to another customer.");
            }
            throw e;
        }
    }

    private static UserDao.Applicant applicant(UserAccount user) throws SQLException {
        if (user == null || !user.isApplicant()) {
            throw new AppException("Only applicants can do this.");
        }
        return Database.withConnection(c -> new UserDao(c).applicant(user.username()))
                .orElseThrow(() -> new AppException("Your applicant account couldn't be found. Sign in again."));
    }

    private static void require(String field, String value, int max) {
        String v = text(value);
        if (v.isEmpty()) {
            throw new AppException(field + " is required.");
        }
        if (v.length() > max) {
            throw new AppException(field + " must be at most " + max + " characters.");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
