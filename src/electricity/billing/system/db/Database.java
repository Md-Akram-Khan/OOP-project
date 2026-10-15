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
        DriverManager.setLoginTimeout(15);
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

    public static boolean isUniqueViolation(SQLException e) {
        return UNIQUE_VIOLATION.equals(e.getSQLState());
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(
                Config.require("SUPABASE_DB_URL"),
                Config.require("SUPABASE_DB_USER"),
                Config.require("SUPABASE_DB_PASSWORD"));
    }
}
