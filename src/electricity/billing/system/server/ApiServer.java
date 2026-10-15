package electricity.billing.system.server;

import com.sun.net.httpserver.HttpServer;
import electricity.billing.system.common.Config;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The backend. It is the only program that holds the database password; the
 * desktop app talks to it over HTTP and never touches the database.
 *
 * Settings (environment variables or .env):
 *   SUPABASE_DB_URL, SUPABASE_DB_USER, SUPABASE_DB_PASSWORD   database connection
 *   EBS_SERVER_HOST   address to listen on (default 127.0.0.1, this computer only)
 *   EBS_SERVER_PORT   port to listen on (default 8080)
 */
public final class ApiServer {

    private static final int THREADS = 16;

    private final HttpServer http;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    public ApiServer(InetSocketAddress address) throws IOException {
        Clock clock = Clock.systemUTC();
        SessionStore sessions = new SessionStore(clock);
        Router router = new Router(sessions);
        new Endpoints(sessions, new LoginThrottle(clock)).register(router);

        http = HttpServer.create(address, 0);
        http.createContext("/api/", router);
        http.setExecutor(executor);
    }

    public void start() {
        http.start();
    }

    public void stop() {
        http.stop(1);
        executor.shutdownNow();
    }

    /** The port actually in use, which matters when started on port 0 (any free port). */
    public int port() {
        return http.getAddress().getPort();
    }

    public static void main(String[] args) throws IOException {
        String host = Config.get("EBS_SERVER_HOST", "127.0.0.1");
        int port = Integer.parseInt(Config.get("EBS_SERVER_PORT", "8080"));
        // Fail fast if the database settings are missing, rather than on the first request.
        Config.require("SUPABASE_DB_URL");
        Config.require("SUPABASE_DB_USER");
        Config.require("SUPABASE_DB_PASSWORD");

        ApiServer server = new ApiServer(new InetSocketAddress(host, port));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Electricity Billing API running at http://" + host + ":" + server.port() + "/api");
        System.out.println("Press Ctrl+C to stop.");
    }
}
