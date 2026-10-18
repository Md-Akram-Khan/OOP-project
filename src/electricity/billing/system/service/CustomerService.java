package electricity.billing.system.service;

import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Validation;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.meterphoto.MeterPhotoReader;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adding, reading and updating customers and their meters. */
public class CustomerService {

    /** A new customer without a meter yet; see {@link #create(Customer, Meter)}. */
    public Customer create(Customer draft) throws SQLException {
        return create(draft, null);
    }

    /**
     * Saves a new customer under the meter number the admin typed (the number
     * printed on the physical meter), reserves their login, and sets up their
     * meter, all in one transaction: either everything is saved or nothing is,
     * so a failure can never leave a customer without a meter. Meter numbers
     * are unique. The meter's serial is the meter number, which meter photos
     * are checked against.
     */
    public Customer create(Customer draft, Meter settings) throws SQLException {
        Validation.customer(draft);
        Customer customer = draft.withMeterNo(Validation.meterNumber(draft.meterNo()));
        Meter meter = settings == null ? null : new Meter(customer.meterNo(), settings.meterType(), settings.billType(),
                Meter.DEFAULT_BILLING_DAYS, settings.sanctionedLoadKw(), customer.meterNo());
        if (meter != null) {
            validateMeter(meter);
        }
        try {
            Database.inTransaction(c -> {
                CustomerDao customers = new CustomerDao(c);
                if (customers.exists(customer.meterNo())) {
                    throw new AppException("Meter number " + customer.meterNo() + " is already registered to another customer. "
                            + "Check the number on the meter.");
                }
                MeterDao meters = new MeterDao(c);
                String owner = meters.serialOwner(customer.meterNo());
                if (owner != null) {
                    throw new AppException("Meter number " + customer.meterNo() + " is already on file for customer " + owner
                            + ". Check the number on the meter.");
                }
                customers.insert(customer);
                new UserDao(c).insertCustomerPlaceholder(customer.meterNo(), customer.name());
                if (meter != null) {
                    meters.save(meter);
                }
                return null;
            });
        } catch (SQLException e) {
            if (Database.isUniqueViolation(e)) {
                throw new AppException("Meter number " + customer.meterNo() + " is already registered to another customer.");
            }
            throw e;
        }
        return customer;
    }

    public void updateContact(Customer customer) throws SQLException {
        Validation.customer(customer);
        int updated = Database.withConnection(c -> new CustomerDao(c).updateContact(customer));
        if (updated == 0) {
            throw new AppException("No customer has meter number " + customer.meterNo() + ".");
        }
    }

    public Optional<Customer> find(String meterNo) throws SQLException {
        return Database.withConnection(c -> new CustomerDao(c).find(meterNo));
    }

    public List<Customer> findAll() throws SQLException {
        return Database.withConnection(c -> new CustomerDao(c).findAll());
    }

    public List<String> meterNumbers() throws SQLException {
        return Database.withConnection(c -> new CustomerDao(c).meterNumbers());
    }

    public Optional<Meter> meter(String meterNo) throws SQLException {
        return Database.withConnection(c -> new MeterDao(c).find(meterNo));
    }

    public void saveMeter(Meter meter) throws SQLException {
        validateMeter(meter);
        Database.withConnection(c -> {
            if (!new CustomerDao(c).exists(meter.meterNo())) {
                throw new AppException("No customer has meter number " + meter.meterNo() + ".");
            }
            try {
                new MeterDao(c).save(meter);
            } catch (SQLException e) {
                if (Database.isUniqueViolation(e)) {
                    throw new AppException("Another customer's meter already has the number " + meter.serialNo().trim() + ".");
                }
                throw e;
            }
            return null;
        });
    }

    private static void validateMeter(Meter meter) {
        if (meter.billType() == null || !Meter.TYPES.contains(meter.meterType())) {
            throw new AppException("Choose a meter type and a bill type.");
        }
        if (meter.days() <= 0) {
            throw new AppException("Billing days must be positive.");
        }
        if (meter.serialNo() != null && !meter.serialNo().isBlank()) {
            int length = MeterPhotoReader.normalizeSerial(meter.serialNo()).length();
            if (length < 4 || length > 20 || meter.serialNo().trim().length() > 30) {
                throw new AppException("Enter the meter's serial exactly as printed on it, e.g. E-756216.");
            }
        }
        if (meter.sanctionedLoadKw() != null && (meter.sanctionedLoadKw().signum() <= 0
                || meter.sanctionedLoadKw().compareTo(Meter.MAX_LOAD_KW) > 0)) {
            throw new AppException("The sanctioned load must be between 0.1 and " + Meter.MAX_LOAD_KW + " kW.");
        }
    }
}
