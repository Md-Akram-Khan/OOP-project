package electricity.billing.system.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import electricity.billing.system.common.Api;
import electricity.billing.system.common.AppException;
import electricity.billing.system.common.Json;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Sends each request to its endpoint and turns the result, or the failure, into a JSON response. */
final class Router implements HttpHandler {

    @FunctionalInterface
    interface Endpoint {
        /** Returns the object to send back as JSON, or null for "204 No Content". */
        Object handle(Request request) throws Exception;
    }

    private static final Logger LOG = Logger.getLogger(Router.class.getName());

    private final Map<String, Endpoint> endpoints = new HashMap<>();
    private final SessionStore sessions;

    Router(SessionStore sessions) {
        this.sessions = sessions;
    }

    void add(String method, String path, Endpoint endpoint) {
        endpoints.put(method + " " + path, endpoint);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            Endpoint endpoint = endpoints.get(exchange.getRequestMethod() + " " + path);
            if (endpoint == null) {
                sendError(exchange, 404, "Not found.");
                return;
            }
            Object result;
            try {
                result = endpoint.handle(new Request(exchange, sessions));
            } catch (ApiError e) {
                sendError(exchange, e.status(), e.getMessage());
                return;
            } catch (AppException e) {
                sendError(exchange, 400, e.getMessage());
                return;
            } catch (Exception e) {
                // Details stay in the server log; the client only learns that something failed.
                LOG.log(Level.SEVERE, exchange.getRequestMethod() + " " + path + " failed", e);
                sendError(exchange, 500, "The server hit an unexpected error. Please try again.");
                return;
            }
            if (result == null) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                send(exchange, 200, Json.GSON.toJson(result));
            }
        } finally {
            exchange.close();
        }
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, Json.GSON.toJson(new Api.ErrorResponse(message)));
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
