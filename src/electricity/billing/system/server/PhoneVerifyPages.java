package electricity.billing.system.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Json;
import electricity.billing.system.service.SignupService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The browser page for verifying a phone number with Firebase Phone
 * Authentication. Firebase needs a browser for its reCAPTCHA check, so the app
 * opens this page; Firebase texts the code to the phone, the page sends the
 * resulting ID token back here, and the server checks it with Google's keys.
 *
 * GET  /verify-phone?session=...   the page
 * POST /verify-phone/complete      {session, idToken} from the page
 */
final class PhoneVerifyPages implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(PhoneVerifyPages.class.getName());
    private static final String FIREBASE_SDK = "https://www.gstatic.com/firebasejs/10.12.2/";

    private final SignupService signup;

    PhoneVerifyPages(SignupService signup) {
        this.signup = signup;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.equals("/verify-phone/complete") && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonObject body = JsonParser.parseString(read(exchange)).getAsJsonObject();
                signup.completeBrowserVerification(body.get("session").getAsString(), body.get("idToken").getAsString());
                send(exchange, 200, "application/json", "{\"ok\":true}");
                return;
            }
            if (path.equals("/verify-phone") && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String session = query(exchange, "session");
                String phone = signup.browserSessionPhone(session);
                send(exchange, 200, "text/html; charset=utf-8", page(session, phone));
                return;
            }
            send(exchange, 404, "text/plain; charset=utf-8", "Not found");
        } catch (AppException e) {
            if (path.endsWith("/complete")) {
                send(exchange, 400, "application/json", "{\"error\":" + Json.GSON.toJson(e.getMessage()) + "}");
            } else {
                send(exchange, 400, "text/html; charset=utf-8", message(e.getMessage()));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Phone verification page failed", e);
            send(exchange, 500, "text/plain; charset=utf-8", "Something went wrong. Start again in the app.");
        }
    }

    private String page(String session, String phone) {
        SignupService.FirebaseConfig f = signup.firebase();
        String config = Json.GSON.toJson(new FirebaseWeb(f.apiKey(), f.authDomain(), f.projectId()));
        String e164 = "+88" + phone;
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Verify your phone</title>
                <style>
                  body{margin:0;font-family:Segoe UI,Arial,sans-serif;background:#0a1024;color:#101828;display:flex;min-height:100vh;
                       align-items:center;justify-content:center}
                  .card{background:#fff;border-radius:18px;padding:32px 34px;width:min(420px,90vw);box-shadow:0 20px 60px #0006}
                  h1{margin:0 0 6px;font-size:22px} p{color:#667085;font-size:14px;line-height:1.5;margin:6px 0 16px}
                  .phone{font-size:20px;font-weight:600;margin:8px 0 18px}
                  input{width:100%;box-sizing:border-box;font-size:22px;letter-spacing:6px;padding:12px 14px;border:1px solid #d0d5dd;
                        border-radius:10px;margin:8px 0 14px;text-align:center}
                  button{width:100%;padding:13px;border:0;border-radius:10px;font-size:15px;font-weight:600;color:#fff;cursor:pointer;
                         background:linear-gradient(90deg,#2f6bff,#00c9b7)} button:disabled{opacity:.5;cursor:default}
                  .err{color:#f04438;font-size:14px;min-height:20px;margin-top:10px} .ok{color:#12b76a;font-size:18px;font-weight:600}
                  #recaptcha{margin:10px 0}
                </style></head><body><div class="card">
                <h1>Verify your phone</h1>
                <p>Firebase will text a 6-digit code to:</p>
                <div class="phone">%PHONE%</div>
                <div id="step1"><div id="recaptcha"></div><button id="send">Send code</button></div>
                <div id="step2" style="display:none"><p>Enter the code from the SMS:</p>
                  <input id="code" inputmode="numeric" maxlength="6" autocomplete="one-time-code"><button id="verify">Verify</button></div>
                <div id="done" style="display:none"><p class="ok">✓ Phone verified</p><p>You can close this tab and go back to the app.</p></div>
                <div class="err" id="err"></div>
                </div>
                <script src="%SDK%firebase-app-compat.js"></script>
                <script src="%SDK%firebase-auth-compat.js"></script>
                <script>
                  const session = %SESSION%, phone = %E164%;
                  firebase.initializeApp(%CONFIG%);
                  const auth = firebase.auth();
                  auth.languageCode = 'en';
                  const err = m => document.getElementById('err').textContent = m || '';
                  const verifier = new firebase.auth.RecaptchaVerifier('recaptcha', {size: 'normal'});
                  verifier.render();
                  let confirmation = null;
                  document.getElementById('send').onclick = async () => {
                    err(''); const b = document.getElementById('send'); b.disabled = true; b.textContent = 'Sending…';
                    try {
                      confirmation = await auth.signInWithPhoneNumber(phone, verifier);
                      document.getElementById('step1').style.display = 'none';
                      document.getElementById('step2').style.display = 'block';
                      document.getElementById('code').focus();
                    } catch (e) {
                      err(e.message || 'The SMS could not be sent.'); b.disabled = false; b.textContent = 'Send code';
                      verifier.render().then(id => grecaptcha.reset(id));
                    }
                  };
                  document.getElementById('verify').onclick = async () => {
                    err(''); const b = document.getElementById('verify'); b.disabled = true; b.textContent = 'Checking…';
                    try {
                      const result = await confirmation.confirm(document.getElementById('code').value.trim());
                      const idToken = await result.user.getIdToken();
                      const r = await fetch('/verify-phone/complete', {method: 'POST', headers: {'Content-Type': 'application/json'},
                                            body: JSON.stringify({session, idToken})});
                      const j = await r.json();
                      if (!r.ok) throw new Error(j.error || 'The server could not confirm the verification.');
                      await auth.signOut();
                      document.getElementById('step2').style.display = 'none';
                      document.getElementById('done').style.display = 'block';
                    } catch (e) {
                      err(e.code === 'auth/invalid-verification-code' ? 'Wrong code. Check the SMS and try again.' : (e.message || 'Verification failed.'));
                      b.disabled = false; b.textContent = 'Verify';
                    }
                  };
                </script></body></html>
                """
                .replace("%SDK%", FIREBASE_SDK)
                .replace("%PHONE%", escape(phone))
                .replace("%SESSION%", Json.GSON.toJson(session))
                .replace("%E164%", Json.GSON.toJson(e164))
                .replace("%CONFIG%", config);
    }

    private record FirebaseWeb(String apiKey, String authDomain, String projectId) {
    }

    private static String message(String text) {
        return "<!doctype html><html><head><meta charset='utf-8'><title>Verify your phone</title></head>"
                + "<body style='font-family:Segoe UI,Arial,sans-serif;padding:40px'><h2>Phone verification</h2><p>"
                + escape(text) + "</p></body></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String query(HttpExchange exchange, String name) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return null;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String read(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readNBytes(16 * 1024 + 1);
            if (bytes.length > 16 * 1024) {
                throw new AppException("Request too large.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
