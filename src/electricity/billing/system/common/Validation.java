package electricity.billing.system.common;

import electricity.billing.system.model.Customer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Input rules shared by both sides: the app checks them for instant feedback,
 * and the server checks them again because it never trusts the app.
 */
public final class Validation {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 -]{6,18}");

    private Validation() {
    }

    /** Throws an AppException listing every problem with the customer's details. */
    public static void customer(Customer customer) {
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
