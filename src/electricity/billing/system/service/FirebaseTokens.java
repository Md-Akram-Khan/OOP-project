package electricity.billing.system.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import electricity.billing.system.common.AppException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks Firebase ID tokens, the proof Firebase gives a browser after it has
 * verified a phone number by SMS.
 *
 * A token is only accepted if Google signed it (RS256, checked against
 * Google's published certificates), it was issued for this Firebase project,
 * it hasn't expired, and it carries a phone number. Nothing the browser says
 * is trusted without that signature.
 */
public class FirebaseTokens {

    private static final String CERTS_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
    private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");
    private static final long CLOCK_SKEW_SECONDS = 60;

    /** What a verified token says. */
    public record Verified(String uid, String phoneNumber) {
    }

    private final String projectId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private Map<String, PublicKey> keys = Map.of();
    private long keysExpire;

    public FirebaseTokens(String projectId) {
        this.projectId = projectId;
    }

    /** For tests: check tokens against these keys instead of fetching Google's. */
    FirebaseTokens(String projectId, Map<String, PublicKey> fixedKeys) {
        this.projectId = projectId;
        this.keys = Map.copyOf(fixedKeys);
        this.keysExpire = Long.MAX_VALUE;
    }

    /** @throws AppException if the token isn't a genuine, current Firebase phone sign-in for this project */
    public Verified verify(String idToken) {
        String[] parts = idToken == null ? new String[0] : idToken.split("\\.");
        if (parts.length != 3) {
            throw new AppException("The phone verification couldn't be confirmed. Try again.");
        }
        JsonObject header = json(parts[0]);
        JsonObject claims = json(parts[1]);
        if (!"RS256".equals(str(header, "alg"))) {
            throw new AppException("The phone verification couldn't be confirmed. Try again.");
        }
        PublicKey key = key(str(header, "kid"));
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(key);
            rsa.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!rsa.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new AppException("The phone verification couldn't be confirmed. Try again.");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("The phone verification couldn't be confirmed. Try again.");
        }
        long now = System.currentTimeMillis() / 1000;
        boolean ok = projectId.equals(str(claims, "aud"))
                && ("https://securetoken.google.com/" + projectId).equals(str(claims, "iss"))
                && num(claims, "exp") > now - CLOCK_SKEW_SECONDS
                && num(claims, "iat") <= now + CLOCK_SKEW_SECONDS
                && num(claims, "auth_time") <= now + CLOCK_SKEW_SECONDS
                && !str(claims, "sub").isEmpty();
        if (!ok) {
            throw new AppException("The phone verification has expired or isn't for this app. Try again.");
        }
        String phone = str(claims, "phone_number");
        if (phone.isEmpty()) {
            throw new AppException("Firebase didn't confirm a phone number. Try again.");
        }
        return new Verified(str(claims, "sub"), phone);
    }

    /** Google's signing key with this id, refreshing the cached certificates when they expire. */
    private synchronized PublicKey key(String kid) {
        if (System.currentTimeMillis() > keysExpire || !keys.containsKey(kid)) {
            refreshKeys();
        }
        PublicKey key = keys.get(kid);
        if (key == null) {
            throw new AppException("The phone verification couldn't be confirmed. Try again.");
        }
        return key;
    }

    private void refreshKeys() {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(CERTS_URL))
                    .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonObject certs = JsonParser.parseString(response.body()).getAsJsonObject();
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Map<String, PublicKey> fresh = new HashMap<>();
            for (String kid : certs.keySet()) {
                X509Certificate cert = (X509Certificate) factory.generateCertificate(
                        new ByteArrayInputStream(certs.get(kid).getAsString().getBytes(StandardCharsets.US_ASCII)));
                fresh.put(kid, cert.getPublicKey());
            }
            keys = fresh;
            long maxAge = 3600;
            Matcher m = MAX_AGE.matcher(response.headers().firstValue("cache-control").orElse(""));
            if (m.find()) {
                maxAge = Long.parseLong(m.group(1));
            }
            keysExpire = System.currentTimeMillis() + maxAge * 1000;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AppException("Couldn't reach Google to confirm the phone verification. Check the internet connection.");
        } catch (Exception e) {
            throw new AppException("Google's signing certificates couldn't be read.");
        }
    }

    private static JsonObject json(String base64url) {
        try {
            return JsonParser.parseString(new String(Base64.getUrlDecoder().decode(base64url), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (RuntimeException e) {
            throw new AppException("The phone verification couldn't be confirmed. Try again.");
        }
    }

    private static String str(JsonObject o, String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : "";
    }

    private static long num(JsonObject o, String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsLong() : 0;
    }
}
