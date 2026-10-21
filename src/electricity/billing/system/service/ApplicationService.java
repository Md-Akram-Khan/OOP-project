package electricity.billing.system.service;

import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Places;
import electricity.billing.system.common.Validation;
import electricity.billing.system.db.ApplicationDao;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.NidDao;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import electricity.billing.system.model.MeterApplication;
import electricity.billing.system.model.MeterApplication.Address;
import electricity.billing.system.model.MeterApplication.ConsumerType;
import electricity.billing.system.model.NidRecord;
import electricity.billing.system.model.TariffCategory;
import electricity.billing.system.model.UserAccount;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Applications for a new meter: applicants submit them, admins review them,
 * and once the meter is installed, connecting it creates the customer.
 */
public class ApplicationService {

    /** Above this load a supply is three-phase, as on DPDC's form. */
    private static final BigDecimal SINGLE_PHASE_MAX_KW = new BigDecimal("7");
    /** The least load per meter. */
    private static final BigDecimal MIN_KW_PER_METER = new BigDecimal("2");
    private static final Pattern BD_MOBILE = Pattern.compile("01[3-9][0-9]{8}");
    private static final Pattern TELEPHONE = Pattern.compile("[0-9]{6,11}");
    private static final Pattern POST_CODE = Pattern.compile("[0-9]{4}");
    /** Names: letters (any script, with their marks), spaces, and . - ' */
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*");

    public Api.ApplicantProfile profile(UserAccount user) throws SQLException {
        UserDao.Applicant a = applicant(user);
        NidRecord nid = nidRecord(a);
        return new Api.ApplicantProfile(a.username(), a.name(), a.nidNo(), a.phone(), nid == null ? null : nid.dateOfBirth(),
                nid == null ? null : nid.fatherBn(), nid == null ? null : nid.motherBn());
    }

    /**
     * An applicant applies. Name, NID number, date of birth and phone come from
     * their verification, not the form. Every problem with the form is reported
     * at once, each named the way the form shows it.
     */
    public MeterApplication submit(UserAccount user, Api.NewApplication f) throws SQLException {
        UserDao.Applicant who = applicant(user);
        NidRecord nid = nidRecord(who);
        List<String> problems = new ArrayList<>();

        ConsumerType type = f.consumerType();
        String father = null;
        String mother = null;
        String spouse = null;
        String organization = null;
        if (type == null) {
            problems.add("Choose the consumer type: personal or organization.");
        } else if (type == ConsumerType.PERSONAL) {
            father = name(problems, "Father's name", f.fatherName(), true);
            mother = name(problems, "Mother's name", f.motherName(), true);
            spouse = name(problems, "Spouse name", f.spouseName(), false);
            if (f.gender() == null) {
                problems.add("Choose your gender.");
            }
        } else {
            organization = optional(problems, "Organization name", f.organizationName(), 50);
            if (organization == null) {
                problems.add("Organization name is required.");
            }
        }

        Address mailing = address(problems, "Mailing address", f.mailing(), false);
        Address connection = address(problems, "Connection address", f.connection(), true);
        String area = optional(problems, "Connection address: Area", f.area(), 60);
        if (area == null) {
            problems.add("Connection address: Area is required.");
        }
        String nocs = optional(problems, "Connection address: NOCS", f.nocs(), 60);

        if (f.connectionType() == null || f.phase() == null || f.ownership() == null) {
            problems.add("Choose the connection type, the phase, and whether you own the premises.");
        }
        TariffCategory tariff = f.tariffCategory() == null ? null : TariffCategory.of(f.tariffCategory()).orElse(null);
        if (tariff == null) {
            problems.add("Choose the tariff category of the connection.");
        } else if (!tariff.available()) {
            problems.add(tariff.code() + " is for loads over " + Meter.MAX_LOAD_KW.toPlainString() + " kW, which are connected "
                    + "directly by the utility, not through this form.");
        }
        Integer meters = f.meterCount();
        if (meters == null || meters < 1 || meters > 999) {
            problems.add("Demand meter: enter how many meters you need (at least 1).");
            meters = null;
        }
        BigDecimal load = f.loadKw();
        if (load == null || load.signum() <= 0) {
            problems.add("Total demand load: enter the load you need in kW, e.g. 2.");
            load = null;
        } else if (load.compareTo(Meter.MAX_LOAD_KW) > 0) {
            problems.add("Total demand load can be at most " + Meter.MAX_LOAD_KW.toPlainString() + " kW.");
        } else if (meters != null && load.compareTo(MIN_KW_PER_METER.multiply(BigDecimal.valueOf(meters))) < 0) {
            problems.add("The minimum load per meter is 2 kW: for " + meters + (meters == 1 ? " meter" : " meters")
                    + ", the total demand load must be at least " + 2 * meters + " kW.");
        }
        if (meters != null && meters > 1 && f.differentOwners() == null) {
            problems.add("Say whether the meters' owners are different people.");
        }
        Integer existingMeters = f.existingMeters();
        if (existingMeters != null && (existingMeters < 0 || existingMeters > 999)) {
            problems.add("No. of existing meters must be between 0 and 999.");
        }
        BigDecimal existingLoad = f.existingLoadKw();
        if (existingLoad != null && existingLoad.signum() < 0) {
            problems.add("Total existing load can't be negative.");
        }
        String existingCustomer = null;
        if (existingMeters != null && existingMeters > 0 && !text(f.existingCustomerNo()).isEmpty()) {
            try {
                existingCustomer = Validation.meterNumber(f.existingCustomerNo());
            } catch (AppException e) {
                problems.add("Existing customer number: " + e.getMessage());
            }
        }
        if (tariff != null && tariff.available() && load != null) {
            BigDecimal total = load.add(existingLoad == null ? BigDecimal.ZERO : existingLoad);
            if (!tariff.fits(total)) {
                problems.add("The total load (" + total.stripTrailingZeros().toPlainString() + " kW"
                        + (existingLoad == null || existingLoad.signum() == 0 ? "" : " with the existing load")
                        + ") doesn't fit " + tariff.code() + " (" + tariff.range() + "). Choose the matching tariff category.");
            }
        }
        if (f.phase() == MeterApplication.Phase.SINGLE && ((load != null && load.compareTo(SINGLE_PHASE_MAX_KW) > 0)
                || (tariff != null && tariff.threePhase()))) {
            problems.add("A load over 7 kW, or a medium-tension tariff, needs a three-phase supply.");
        }
        String notes = text(f.notes());
        if (notes.length() > 500) {
            problems.add("Keep the notes under 500 characters.");
        }
        if (!f.declarationAccepted()) {
            problems.add("You must agree to the declaration first.");
        }
        if (!problems.isEmpty()) {
            throw new AppException(String.join("\n", problems));
        }

        MeterApplication draft = new MeterApplication(0, who.username(), who.name(), who.nidNo(), who.phone(),
                nid == null ? null : nid.dateOfBirth(), type, father, mother, spouse,
                type == ConsumerType.PERSONAL ? f.gender() : null, organization, mailing, connection, area, nocs,
                f.connectionType(), f.phase(), tariff.code(), tariff.billType(), meters, load,
                meters > 1 ? f.differentOwners() : null, existingMeters, existingLoad, existingCustomer, f.ownership(),
                notes.isEmpty() ? null : notes, MeterApplication.Status.SUBMITTED, null, null, null, null);
        return Database.inTransaction(c -> {
            ApplicationDao applications = new ApplicationDao(c);
            if (applications.list(who.id()).stream().anyMatch(a -> a.status().isOpen())) {
                throw new AppException("You already have an application in progress. You can apply again once it's decided.");
            }
            long id = applications.insert(who.id(), draft);
            return applications.find(id).orElseThrow();
        });
    }

    /**
     * One address from the form, checked: house, district (in its division),
     * thana and mobile are required; the connection address needs an email too
     * (bills and online payment receipts go there).
     */
    private static Address address(List<String> problems, String section, Address a, boolean emailRequired) {
        if (a == null) {
            problems.add(section + " is required.");
            return null;
        }
        String house = optional(problems, section + ": House/Plot/Dag No", a.house(), 60);
        if (house == null) {
            problems.add(section + ": House/Plot/Dag No is required.");
        }
        String road = optional(problems, section + ": Lane/Road No", a.road(), 80);
        String sectionNo = optional(problems, section + ": Section", a.section(), 40);
        String block = optional(problems, section + ": Block", a.block(), 40);
        String division = text(a.division());
        String district = text(a.district());
        if (!Places.DIVISIONS.contains(division)) {
            problems.add(section + ": choose the division.");
        } else if (!Places.inDivision(district, division)) {
            problems.add(section + ": choose the district.");
        }
        String thana = optional(problems, section + ": Thana", a.thana(), 40);
        if (thana == null) {
            problems.add(section + ": Thana is required.");
        }
        String postCode = optional(problems, section + ": Post code", a.postCode(), 4);
        if (postCode != null && !POST_CODE.matcher(postCode).matches()) {
            problems.add(section + ": Post code is 4 digits, e.g. 1207.");
        }
        String email = optional(problems, section + ": E-mail", a.email(), 100);
        if (email == null && emailRequired) {
            problems.add(section + ": E-mail is required (your bills and payment receipts are sent there).");
        } else if (email != null && !Validation.isEmail(email)) {
            problems.add(section + ": E-mail doesn't look like an email address.");
        }
        String telephone = digits(a.telephone());
        if (!telephone.isEmpty() && !TELEPHONE.matcher(telephone).matches()) {
            problems.add(section + ": Telephone must be 6-11 digits.");
        }
        String mobile = digits(a.mobile());
        if (mobile.startsWith("880")) {
            mobile = mobile.substring(2);
        }
        if (mobile.isEmpty()) {
            problems.add(section + ": Mobile is required.");
        } else if (!BD_MOBILE.matcher(mobile).matches()) {
            problems.add(section + ": Mobile must be 11 digits starting with 01, e.g. 01712345678.");
        }
        return new Address(house, road, sectionNo, block, thana, district, division, postCode, email,
                telephone.isEmpty() ? null : telephone, mobile);
    }

    /** A name field: letters, spaces and . - ' only, at most 80 characters; null when left blank and optional. */
    private static String name(List<String> problems, String field, String value, boolean required) {
        String v = optional(problems, field, value, 80);
        if (v == null) {
            if (required) {
                problems.add(field + " is required.");
            }
            return null;
        }
        if (!NAME.matcher(v).matches()) {
            problems.add(field + " can only have letters, spaces and . - '");
        }
        return v;
    }

    /** The trimmed text, or null when blank; notes a problem if it's longer than {@code max}. */
    private static String optional(List<String> problems, String field, String value, int max) {
        String v = text(value);
        if (v.length() > max) {
            problems.add(field + " must be at most " + max + " characters.");
        }
        return v.isEmpty() ? null : v;
    }

    private static String digits(String value) {
        return text(value).replaceAll("[\\s-]", "").replaceFirst("^\\+", "");
    }

    private static NidRecord nidRecord(UserDao.Applicant who) throws SQLException {
        return who.nidNo() == null ? null : Database.withConnection(c -> new NidDao(c).find(who.nidNo())).orElse(null);
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
                Address at = app.connection();
                // Bills go to the organization for an organization's connection, otherwise to the applicant.
                Customer customer = new Customer(meterNo, app.consumerName(), address.length() > 100 ? address.substring(0, 100) : address,
                        at.district(), at.division(), at.email(), at.mobile() != null ? at.mobile() : app.phone());
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

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
