package electricity.billing.system.server;

import com.sun.net.httpserver.HttpServer;
import electricity.billing.system.common.Config;
import electricity.billing.system.db.Database;
import electricity.billing.system.service.PaymentService;
import electricity.billing.system.service.SslCommerzGateway;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.SQLException;
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
        http = HttpServer.create(address, 0);
        // Where SSLCommerz sends the customer's browser back to. Set EBS_PUBLIC_URL when the server
        // is hosted somewhere other than this computer.
        String publicUrl = Config.get("EBS_PUBLIC_URL", "http://localhost:" + http.getAddress().getPort());
        publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;

        Clock clock = Clock.systemUTC();
        SessionStore sessions = new SessionStore(clock);
        PaymentService payments = new PaymentService();
        Router router = new Router(sessions);
        new Endpoints(sessions, new LoginThrottle(clock), payments, publicUrl).register(router);

        http.createContext("/api/", router);
        http.createContext("/pay/", new PaymentPages(payments));
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

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : " (" + root.getMessage() + ")");
    }

    /** Turns common connection failures into a concrete fix. */
    private static String diagnose(SQLException e) {
        String host = Database.host();
        String nl = System.lineSeparator();
        StringBuilder tip = new StringBuilder("How to fix:" + nl);
        if (host.startsWith("db.") && host.contains(".supabase.co")) {
            tip.append("  This is Supabase's direct address, which only works over IPv6. Most home networks are IPv4-only.")
               .append(nl)
               .append("  Use the Session pooler connection string instead: Supabase > Connect > Session pooler.")
               .append(nl);
        } else if (Database.isConnectionFailure(e)) {
            tip.append("  Check your internet connection, and that the host and port in SUPABASE_DB_URL are right.")
               .append(nl);
        } else {
            tip.append("  Check SUPABASE_DB_USER and SUPABASE_DB_PASSWORD (Supabase > Project Settings > Database).")
               .append(nl);
        }
        if ("environment variable".equals(Config.source("SUPABASE_DB_URL"))) {
            tip.append("  Note: SUPABASE_DB_URL comes from an environment variable, which overrides the .env file.")
               .append(nl)
               .append("  Remove that variable to use .env, or correct it.");
        } else {
            tip.append("  Settings are read from the .env file in the project folder.");
        }
        return tip.toString();
    }

    public static void main(String[] args) throws IOException {
        String host = Config.get("EBS_SERVER_HOST", "127.0.0.1");
        int port = Integer.parseInt(Config.get("EBS_SERVER_PORT", "8080"));
        // Fail fast, with a clear reason, if the database settings are missing or don't work.
        for (String name : new String[] {"SUPABASE_DB_URL", "SUPABASE_DB_USER", "SUPABASE_DB_PASSWORD"}) {
            Config.require(name);
            System.out.println("  " + name + " from " + Config.source(name));
        }
        System.out.println("Checking database connection to " + Database.host() + " ...");
        try {
            Database.checkConnection();
            System.out.println("Database connection OK.");
        } catch (SQLException e) {
            System.err.println();
            System.err.println("Can't connect to the database at " + Database.host() + ": " + rootMessage(e));
            System.err.println(diagnose(e));
            System.exit(1);
        }

        ApiServer server = new ApiServer(new InetSocketAddress(host, port));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Electricity Billing API running at http://" + host + ":" + server.port() + "/api");
        System.out.println(SslCommerzGateway.isConfigured()
                ? "Online payments: SSLCommerz " + (SslCommerzGateway.isSandbox() ? "SANDBOX (test mode, no real money)" : "LIVE")
                : "Online payments: off (add SSLCOMMERZ_STORE_ID and SSLCOMMERZ_STORE_PASSWORD to .env to enable)");
        System.out.println("Press Ctrl+C to stop.");
    }
}
