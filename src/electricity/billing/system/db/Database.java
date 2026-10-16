package electricity.billing.system.db;

import electricity.billing.system.common.Config;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens connections to the Supabase PostgreSQL database. Only the server uses
 * this class, so only the server needs the SUPABASE_* settings.
 *
 * Every connection is opened for one unit of work and closed straight after,
 * so nothing leaks.
 */
public final class Database {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final String UNIQUE_VIOLATION = "23505";

    static {
        // Two attempts (see open) must fit inside the app's 20-second request timeout.
        DriverManager.setLoginTimeout(8);
    }

    private Database() {
    }

    /** Runs work on a fresh connection and always closes it. */
    public static <T> T withConnection(SqlWork<T> work) throws SQLException {
        try (Connection c = open()) {
            return work.run(c);
        }
    }

    /** Runs work in one transaction: commits if it returns, rolls back if it throws. */
    public static <T> T inTransaction(SqlWork<T> work) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Opens and closes one connection, so start-up fails early with a clear reason. */
    public static void checkConnection() throws SQLException {
        try (Connection c = open()) {
            c.isValid(5);
        }
    }

    /** True if the failure means the database couldn't be reached at all (network, DNS, outage). */
    public static boolean isConnectionFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                return true;
            }
            if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException
                    || t instanceof java.net.SocketTimeoutException || t instanceof java.net.NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }

    /** The host:port part of the JDBC URL, safe to print (no credentials). */
    public static String host() {
        String url = Config.get("SUPABASE_DB_URL", "");
        int start = url.indexOf("//");
        if (start < 0) {
            return "(unknown)";
        }
        int end = url.indexOf('/', start + 2);
        return url.substring(start + 2, end < 0 ? url.length() : end);
    }

    public static boolean isUniqueViolation(SQLException e) {
        return UNIQUE_VIOLATION.equals(e.getSQLState());
    }

    /**
     * Supabase's connection pooler occasionally stalls a new connection during
     * the TLS handshake. One quick retry turns that into a short delay instead
     * of an error for the user.
     */
    private static Connection open() throws SQLException {
        try {
            return connect();
        } catch (SQLException e) {
            if (!isConnectionFailure(e)) {
                throw e;
            }
            return connect();
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                Config.require("SUPABASE_DB_URL"),
                Config.require("SUPABASE_DB_USER"),
                Config.require("SUPABASE_DB_PASSWORD"));
    }
}
