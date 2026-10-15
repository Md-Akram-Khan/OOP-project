package electricity.billing.system.server;

/** An HTTP error with a message that is safe to show the user. */
final class ApiError extends RuntimeException {

    private final int status;

    private ApiError(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }

    static ApiError badRequest(String message) {
        return new ApiError(400, message);
    }

    static ApiError unauthorized() {
        return new ApiError(401, "Please log in.");
    }

    static ApiError forbidden() {
        return new ApiError(403, "You don't have permission to do that.");
    }

    static ApiError notFound(String message) {
        return new ApiError(404, message);
    }

    static ApiError tooLarge() {
        return new ApiError(413, "Request is too large.");
    }

    static ApiError tooManyRequests(String message) {
        return new ApiError(429, message);
    }
}
