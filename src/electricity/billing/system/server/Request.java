package electricity.billing.system.server;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import electricity.billing.system.common.Json;
import electricity.billing.system.model.Months;
import electricity.billing.system.model.UserAccount;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** One incoming HTTP request, with helpers for reading it and checking who sent it. */
final class Request {

    static final int MAX_BODY_BYTES = 64 * 1024;
    /** Requests carrying a meter photo: an 8 MB image is about 11 MB as base64 text. */
    static final int MAX_PHOTO_BODY_BYTES = 12 * 1024 * 1024;
    /** Requests carrying two photos, such as both sides of an NID card. */
    static final int MAX_TWO_PHOTO_BODY_BYTES = 2 * MAX_PHOTO_BODY_BYTES;
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final HttpExchange exchange;
    private final SessionStore sessions;
    private final Map<String, String> query;

    Request(HttpExchange exchange, SessionStore sessions) {
        this.exchange = exchange;
        this.sessions = sessions;
        this.query = parseQuery(exchange.getRequestURI().getRawQuery());
    }

    <T> T body(Class<T> type) throws IOException {
        return body(type, MAX_BODY_BYTES);
    }

    /** The JSON body, allowing up to {@code maxBytes} (e.g. for photo uploads). */
    <T> T body(Class<T> type, int maxBytes) throws IOException {
        byte[] bytes;
        try (InputStream in = exchange.getRequestBody()) {
            bytes = in.readNBytes(maxBytes + 1);
        }
        if (bytes.length > maxBytes) {
            throw ApiError.tooLarge();
        }
        try {
            T value = Json.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
            if (value == null) {
                throw ApiError.badRequest("Request body is missing.");
            }
            return value;
        } catch (JsonParseException e) {
            throw ApiError.badRequest("Request body isn't valid JSON.");
        }
    }

    /** A query parameter, or null if it's absent or blank. */
    String query(String name) {
        String value = query.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    String month(String name) {
        String month = query(name);
        if (month != null && !Months.isValid(month)) {
            throw ApiError.badRequest("Unknown month: " + month);
        }
        return month;
    }

    Integer year(String name) {
        String text = query(name);
        if (text == null) {
            return null;
        }
        try {
            return checkYear(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Year must be a number.");
        }
    }

    static int checkYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw ApiError.badRequest("Year must be between " + MIN_YEAR + " and " + MAX_YEAR + ".");
        }
        return year;
    }

    Optional<String> token() {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return Optional.of(header.substring("Bearer ".length()).trim());
    }

    /** The signed-in user, or empty for an anonymous request. */
    Optional<UserAccount> user() {
        return token().flatMap(sessions::find);
    }

    UserAccount requireUser() {
        return user().orElseThrow(ApiError::unauthorized);
    }

    UserAccount requireAdmin() {
        UserAccount user = requireUser();
        if (!user.isAdmin()) {
            throw ApiError.forbidden();
        }
        return user;
    }

    /**
     * An admin, or a customer with a meter. Applicants (no meter yet) are
     * refused: endpoints that filter by "my meter" must never see a missing one.
     */
    UserAccount requireAdminOrCustomer() {
        UserAccount user = requireUser();
        if (!user.isAdmin() && user.meterNo() == null) {
            throw ApiError.forbidden();
        }
        return user;
    }

    UserAccount requireApplicant() {
        UserAccount user = requireUser();
        if (!user.isApplicant()) {
            throw ApiError.forbidden();
        }
        return user;
    }

    UserAccount requireCustomer() {
        UserAccount user = requireUser();
        if (user.isAdmin() || user.meterNo() == null) {
            throw ApiError.forbidden();
        }
        return user;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.putIfAbsent(
                    URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }
}
