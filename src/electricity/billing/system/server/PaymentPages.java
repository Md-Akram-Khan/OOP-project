package electricity.billing.system.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import electricity.billing.system.common.AppException;
import electricity.billing.system.model.Payment;
import electricity.billing.system.service.PaymentService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The pages SSLCommerz sends the customer's browser back to, plus the IPN
 * (the gateway's own server-to-server notification).
 *
 * Whatever arrives here only tells us which payment to check: the decision is
 * always made by asking SSLCommerz directly (see PaymentService.confirm).
 */
final class PaymentPages implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(PaymentPages.class.getName());
    private static final int MAX_BODY = 32 * 1024;

    private final PaymentService payments;

    PaymentPages(PaymentService payments) {
        this.payments = payments;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> params = new HashMap<>(parse(exchange.getRequestURI().getRawQuery()));
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                parse(readBody(exchange)).forEach(params::putIfAbsent);
            }
            String tranId = first(params.get("tran"), params.get("tran_id"));
            String valId = params.get("val_id");
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/pay/ipn")) {
                if (tranId != null) {
                    payments.confirm(tranId, valId);
                }
                sendText(exchange, 200, "OK");
                return;
            }
            if (tranId == null) {
                sendPage(exchange, 400, Page.ERROR, "This payment link is missing its reference.");
                return;
            }
            Payment.Status status = switch (path) {
                case "/pay/success" -> payments.confirm(tranId, valId);
                case "/pay/fail" -> payments.endIfUnpaid(tranId, Payment.Status.FAILED);
                case "/pay/cancel" -> payments.endIfUnpaid(tranId, Payment.Status.CANCELLED);
                default -> null;
            };
            if (status == null) {
                sendPage(exchange, 404, Page.ERROR, "Page not found.");
            } else if (status == Payment.Status.PAID) {
                sendPage(exchange, 200, Page.PAID, "Your payment went through and your bill is now marked as paid.");
            } else if (status == Payment.Status.INITIATED) {
                sendPage(exchange, 200, Page.WAITING, "We haven't received confirmation from SSLCommerz yet. "
                        + "The app will update as soon as it arrives.");
            } else if (status == Payment.Status.CANCELLED) {
                sendPage(exchange, 200, Page.CANCELLED, "You cancelled the payment. Nothing was charged.");
            } else {
                sendPage(exchange, 200, Page.FAILED, "The payment didn't go through, and nothing was charged.");
            }
        } catch (AppException e) {
            sendPage(exchange, 400, Page.ERROR, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Payment callback failed", e);
            sendPage(exchange, 500, Page.ERROR, "Something went wrong while checking your payment. "
                    + "If you were charged, it will still be confirmed shortly.");
        } finally {
            exchange.close();
        }
    }

    private enum Page {
        PAID("Payment successful", "#12B76A", "&#10003;"),
        WAITING("Confirming your payment", "#2F6BFF", "&#8987;"),
        CANCELLED("Payment cancelled", "#F79009", "&#8592;"),
        FAILED("Payment failed", "#F04438", "&#10005;"),
        ERROR("Something went wrong", "#F04438", "!");

        final String title;
        final String color;
        final String icon;

        Page(String title, String color, String icon) {
            this.title = title;
            this.color = color;
            this.icon = icon;
        }
    }

    private static void sendPage(HttpExchange exchange, int status, Page page, String message) throws IOException {
        String html = """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s · Electricity Billing System</title>
                <style>
                  body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:Inter,Segoe UI,system-ui,sans-serif;
                       background:linear-gradient(135deg,#0A1024,#13245A);color:#101828}
                  .card{background:#fff;border-radius:20px;padding:44px 40px;max-width:420px;margin:16px;text-align:center;
                        box-shadow:0 24px 60px rgba(0,0,0,.35)}
                  .icon{width:76px;height:76px;border-radius:50%%;margin:0 auto 22px;display:grid;place-items:center;
                        font-size:38px;color:#fff;background:%s}
                  h1{font-size:24px;margin:0 0 10px}
                  p{color:#667085;line-height:1.55;margin:0 0 26px}
                  .back{display:inline-block;background:linear-gradient(90deg,#2F6BFF,#00C9B7);color:#fff;text-decoration:none;
                        padding:12px 22px;border-radius:12px;font-weight:600}
                  small{display:block;margin-top:22px;color:#98A2B3}
                </style></head><body><div class="card">
                <div class="icon">%s</div><h1>%s</h1><p>%s</p>
                <span class="back">You can close this tab and return to the app</span>
                <small>Electricity Billing System · payments by SSLCommerz</small>
                </div></body></html>
                """.formatted(page.title, page.color, page.icon, page.title, escape(message));
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(MAX_BODY);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8).trim();
            if (!value.isEmpty()) {
                map.putIfAbsent(key, value);
            }
        }
        return map;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
