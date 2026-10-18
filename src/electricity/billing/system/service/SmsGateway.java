package electricity.billing.system.service;

import electricity.billing.system.common.Config;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Sends text messages (OTP codes).
 *
 * Sending real SMS in Bangladesh needs a paid account with an SMS gateway, so
 * by default the app runs in <b>demo mode</b>: nothing is sent, the code is
 * written to the server console and returned to the app to show on screen.
 *
 * To send real SMS, set in .env:
 * <pre>
 * SMS_MODE=http
 * SMS_HTTP_URL=https://bulksmsbd.net/api/smsapi?api_key=YOUR_KEY&amp;type=text&amp;number={phone}&amp;senderid=YOUR_ID&amp;message={message}
 * </pre>
 * {phone} (as 8801XXXXXXXXX) and {message} are filled in for each SMS.
 */
public interface SmsGateway {

    /** Sends the message. */
    void send(String phone, String message) throws IOException;

    /** True when no SMS is really sent, so the app may show the code instead. */
    boolean isDemo();

    static SmsGateway fromConfig() {
        String mode = Config.get("SMS_MODE", "demo").trim().toLowerCase();
        if (mode.equals("http")) {
            return new Http(Config.require("SMS_HTTP_URL"));
        }
        return new Demo();
    }

    /** Writes messages to the server console instead of sending them. */
    final class Demo implements SmsGateway {
        private static final Logger LOG = Logger.getLogger(SmsGateway.class.getName());

        @Override
        public void send(String phone, String message) {
            LOG.info("DEMO SMS (not sent) to " + phone + ": " + message);
        }

        @Override
        public boolean isDemo() {
            return true;
        }
    }

    /** Calls an SMS gateway's HTTP API built from a URL template. */
    final class Http implements SmsGateway {
        private final String template;
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        Http(String template) {
            this.template = template;
        }

        @Override
        public void send(String phone, String message) throws IOException {
            String international = phone.startsWith("0") ? "88" + phone : phone;
            String url = template.replace("{phone}", URLEncoder.encode(international, StandardCharsets.UTF_8))
                    .replace("{message}", URLEncoder.encode(message, StandardCharsets.UTF_8));
            try {
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("SMS gateway answered HTTP " + response.statusCode());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Sending the SMS was interrupted.");
            }
        }

        @Override
        public boolean isDemo() {
            return false;
        }
    }
}
