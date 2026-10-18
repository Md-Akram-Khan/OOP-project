package electricity.billing.system.client;

import com.google.gson.JsonParseException;
import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Config;
import electricity.billing.system.common.Json;
import electricity.billing.system.model.UserAccount;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Talks to the backend over HTTP and holds the signed-in user's session token.
 * Every failure comes back as an AppException with a message fit for the user.
 *
 * The server address comes from EBS_API_URL (environment variable or .env),
 * defaulting to a server on this computer.
 */
public final class ApiClient {

    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** Requests carrying photos take longer: the upload, and reading the photo on the server. */
    private static final Duration PHOTO_TIMEOUT = Duration.ofSeconds(90);
    private static final int PHOTO_BODY_CHARS = 1_000_000;
    private static volatile ApiClient instance;

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private volatile String token;
    private volatile UserAccount account;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** The app-wide client, created on first use. */
    public static ApiClient get() {
        ApiClient client = instance;
        if (client == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient(Config.get("EBS_API_URL", DEFAULT_URL));
                }
                client = instance;
            }
        }
        return client;
    }

    /** Replaces the app-wide client, e.g. to point tests at a test server. */
    public static void use(ApiClient client) {
        instance = client;
    }

    public UserAccount account() {
        return account;
    }

    void startSession(String newToken, UserAccount newAccount) {
        token = newToken;
        account = newAccount;
    }

    void endSession() {
        token = null;
        account = null;
    }

    /** True if the server answers its health check. */
    public boolean isServerUp() {
        try {
            send("GET", "/api/health", null, null);
            return true;
        } catch (AppException e) {
            return false;
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    <T> T get(String path, Type responseType) {
        return send("GET", path, null, responseType);
    }

    <T> T post(String path, Object body, Type responseType) {
        return send("POST", path, body, responseType);
    }

    <T> T put(String path, Object body, Type responseType) {
        return send("PUT", path, body, responseType);
    }

    /** Builds "path?a=1&b=2", skipping null values. */
    static String withQuery(String path, Map<String, Object> params) {
        StringJoiner query = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null) {
                query.add(key + "=" + URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
            }
        });
        return query.length() == 0 ? path : path + "?" + query;
    }

    static Map<String, Object> params(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private <T> T send(String method, String path, Object body, Type responseType) {
        String json = body == null ? null : Json.GSON.toJson(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(json != null && json.length() > PHOTO_BODY_CHARS ? PHOTO_TIMEOUT : TIMEOUT)
                .header("Accept", "application/json");
        String currentToken = token;
        if (currentToken != null) {
            request.header("Authorization", "Bearer " + currentToken);
        }
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AppException("Can't reach the server at " + baseUrl + ".\nMake sure the server is running.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException("The request was cancelled.");
        }

        int status = response.statusCode();
        if (status == 401 && currentToken != null) {
            endSession();
            throw new AppException("Your session has expired. Please log out and log in again.");
        }
        if (status < 200 || status >= 300) {
            throw new AppException(errorMessage(response));
        }
        if (responseType == null || status == 204 || response.body().isEmpty()) {
            return null;
        }
        try {
            return Json.GSON.fromJson(response.body(), responseType);
        } catch (JsonParseException e) {
            throw new AppException("The server sent a response the app couldn't read.");
        }
    }

    private static String errorMessage(HttpResponse<String> response) {
        try {
            Api.ErrorResponse error = Json.GSON.fromJson(response.body(), Api.ErrorResponse.class);
            if (error != null && error.error() != null) {
                return error.error();
            }
        } catch (JsonParseException ignored) {
            // Not one of our error bodies (e.g. a proxy's HTML page); fall through.
        }
        return "The server returned an error (HTTP " + response.statusCode() + ").";
    }
}
