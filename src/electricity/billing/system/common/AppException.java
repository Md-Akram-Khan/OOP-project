package electricity.billing.system.common;

/** A problem the user can fix, such as invalid input or a broken business rule. The message is shown as-is. */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }
}
