package electricity.billing.system.service;

import electricity.billing.system.db.CustomerDao;
import electricity.billing.system.db.Database;
import electricity.billing.system.db.MeterDao;
import electricity.billing.system.db.UserDao;
import electricity.billing.system.model.Customer;
import electricity.billing.system.model.Meter;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Adding, reading and updating customers and their meters. */
public class CustomerService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 -]{6,18}");
    private static final int METER_NUMBER_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Checks the fields a user typed. Throws an AppException listing every problem. */
    public static void validate(Customer customer) {
        List<String> problems = new ArrayList<>();
        require(problems, "Name", customer.name(), 50);
        require(problems, "Address", customer.address(), 100);
        require(problems, "City", customer.city(), 30);
        require(problems, "State", customer.state(), 30);
        if (require(problems, "Email", customer.email(), 100) && !EMAIL.matcher(customer.email()).matches()) {
            problems.add("Email doesn't look like an email address.");
        }
        if (require(problems, "Phone", customer.phone(), 20) && !PHONE.matcher(customer.phone()).matches()) {
            problems.add("Phone must be 7-15 digits (spaces, dashes and a leading + are allowed).");
        }
        if (!problems.isEmpty()) {
            throw new AppException(String.join("\n", problems));
        }
    }

    /**
     * Saves a new customer under a freshly generated, unique 6-digit meter
     * number, and reserves their login row in the same transaction.
     */
    public Customer create(Customer draft) throws SQLException {
        validate(draft);
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
        validate(customer);
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

    public void saveMeter(Meter meter) throws SQLException {
        Database.withConnection(c -> {
            new MeterDao(c).save(meter);
            return null;
        });
    }

    private static String newMeterNumber() {
        return String.valueOf(100_000 + RANDOM.nextInt(900_000));
    }

    private static boolean require(List<String> problems, String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            problems.add(field + " is required.");
            return false;
        }
        if (value.length() > maxLength) {
            problems.add(field + " must be at most " + maxLength + " characters.");
            return false;
        }
        return true;
    }
}
