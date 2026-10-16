package electricity.billing.system.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Config;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Talks to SSLCommerz (https://developer.sslcommerz.com/doc/v4/).
 *
 * Settings (server .env only, never the app):
 *   SSLCOMMERZ_STORE_ID, SSLCOMMERZ_STORE_PASSWORD   store credentials
 *   SSLCOMMERZ_SANDBOX                               "true" (default) for the free test gateway
 */
public class SslCommerzGateway {

    private static final String SANDBOX = "https://sandbox.sslcommerz.com";
    private static final String LIVE = "https://securepay.sslcommerz.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** What the validation API says about one completed transaction. */
    public record Validation(String status, String tranId, String valId, BigDecimal amount, String currency,
            String bankTranId, String cardType, String riskLevel) {

        public boolean isValid() {
            return "VALID".equals(status) || "VALIDATED".equals(status);
        }
    }

    public record Session(String sessionKey, String gatewayUrl) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public static boolean isConfigured() {
        return Config.get("SSLCOMMERZ_STORE_ID", null) != null && Config.get("SSLCOMMERZ_STORE_PASSWORD", null) != null;
    }

    public static boolean isSandbox() {
        return !"false".equalsIgnoreCase(Config.get("SSLCOMMERZ_SANDBOX", "true"));
    }

    private static String base() {
        return isSandbox() ? SANDBOX : LIVE;
    }

    private static String storeId() {
        return Config.require("SSLCOMMERZ_STORE_ID");
    }

    private static String storePassword() {
        return Config.require("SSLCOMMERZ_STORE_PASSWORD");
    }

    /** Creates a checkout session and returns the page to send the customer to. */
    public Session startSession(Map<String, String> fields) throws IOException, InterruptedException {
        StringJoiner form = new StringJoiner("&");
        form.add(pair("store_id", storeId()));
        form.add(pair("store_passwd", storePassword()));
        fields.forEach((k, v) -> form.add(pair(k, v)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/gwprocess/v4/api.php"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        JsonObject json = send(request).getAsJsonObject();
        if (!"SUCCESS".equalsIgnoreCase(text(json, "status")) || text(json, "GatewayPageURL") == null) {
            String reason = text(json, "failedreason");
            throw new AppException("SSLCommerz couldn't start the payment" + (reason == null || reason.isBlank() ? "." : ": " + reason));
        }
        return new Session(text(json, "sessionkey"), text(json, "GatewayPageURL"));
    }

    /** Asks SSLCommerz, server to server, whether a validation id belongs to a real, completed payment. */
    public Validation validate(String valId) throws IOException, InterruptedException {
        String url = base() + "/validator/api/validationserverAPI.php?" + pair("val_id", valId) + "&"
                + pair("store_id", storeId()) + "&" + pair("store_passwd", storePassword()) + "&format=json";
        return toValidation(send(get(url)).getAsJsonObject());
    }

    /** Every attempt SSLCommerz has recorded for our transaction id, newest information included. */
    public List<Validation> lookup(String tranId) throws IOException, InterruptedException {
        String url = base() + "/validator/api/merchantTransIDvalidationAPI.php?" + pair("tran_id", tranId) + "&"
                + pair("store_id", storeId()) + "&" + pair("store_passwd", storePassword()) + "&format=json";
        JsonElement json = send(get(url));
        List<Validation> results = new ArrayList<>();
        if (json.isJsonObject() && json.getAsJsonObject().get("element") instanceof JsonArray elements) {
            for (JsonElement e : elements) {
                if (e.isJsonObject()) {
                    results.add(toValidation(e.getAsJsonObject()));
                }
            }
        }
        return results;
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
    }

    private JsonElement send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("SSLCommerz returned HTTP " + response.statusCode());
        }
        try {
            return JsonParser.parseString(response.body());
        } catch (RuntimeException e) {
            throw new IOException("SSLCommerz sent a response that isn't JSON");
        }
    }

    private static Validation toValidation(JsonObject o) {
        String amount = text(o, "amount");
        return new Validation(text(o, "status"), text(o, "tran_id"), text(o, "val_id"),
                amount == null || amount.isBlank() ? null : new BigDecimal(amount.trim()),
                text(o, "currency"), text(o, "bank_tran_id"), text(o, "card_type"), text(o, "risk_level"));
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String pair(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
