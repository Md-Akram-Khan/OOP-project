package electricity.billing.system.service;

import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Validation;
import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adding, reading and updating customers and their meters. */
public class CustomerService {

    private static final int METER_NUMBER_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Saves a new customer under a freshly generated, unique 6-digit meter
     * number, and reserves their login row in the same transaction.
     */
    public Customer create(Customer draft) throws SQLException {
        Validation.customer(draft);
        for (int attempt = 0; attempt < METER_NUMBER_ATTEMPTS; attempt++) {
            Customer customer = draft.withMeterNo(newMeterNumber());
            try {
                boolean saved = Database.inTransaction(c -> {
                    CustomerDao customers = new CustomerDao(c);
                    if (customers.exists(customer.meterNo())) {
                        return false;
                    }
                    customers.insert(customer);
                    new UserDao(c).insertCustomerPlaceholder(customer.meterNo(), customer.name());
                    return true;
                });
                if (saved) {
                    return customer;
                }
            } catch (SQLException e) {
                if (!Database.isUniqueViolation(e)) {
                    throw e;
                }
                // Another admin took the same number at the same moment; try a new one.
            }
        }
        throw new AppException("Couldn't generate a unique meter number. Please try again.");
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
        if (meter.billType() == null || !Meter.TYPES.contains(meter.meterType())) {
            throw new AppException("Choose a meter type and a bill type.");
        }
        if (meter.days() <= 0) {
            throw new AppException("Billing days must be positive.");
        }
        if (meter.sanctionedLoadKw() != null && (meter.sanctionedLoadKw().signum() <= 0
                || meter.sanctionedLoadKw().compareTo(Meter.MAX_LOAD_KW) > 0)) {
            throw new AppException("The sanctioned load must be between 0.1 and " + Meter.MAX_LOAD_KW + " kW.");
        }
        Database.withConnection(c -> {
            if (!new CustomerDao(c).exists(meter.meterNo())) {
                throw new AppException("No customer has meter number " + meter.meterNo() + ".");
            }
            new MeterDao(c).save(meter);
            return null;
        });
    }

    private static String newMeterNumber() {
        return String.valueOf(100_000 + RANDOM.nextInt(900_000));
    }
}
