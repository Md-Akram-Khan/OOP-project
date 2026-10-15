package electricity.billing.system.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens connections to the Supabase PostgreSQL database. Settings come from
 * environment variables, falling back to a .env file in the working directory
 * or up to three folders above it.
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
    private static volatile Settings settings;

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
        Settings s = settings();
        return DriverManager.getConnection(s.url(), s.user(), s.password());
    }

    private static Settings settings() {
        Settings s = settings;
        if (s == null) {
            Map<String, String> dotenv = loadDotEnv();
            s = new Settings(
                    requiredSetting("SUPABASE_DB_URL", dotenv),
                    requiredSetting("SUPABASE_DB_USER", dotenv),
                    requiredSetting("SUPABASE_DB_PASSWORD", dotenv));
            settings = s;
        }
        return s;
    }

    private static String requiredSetting(String name, Map<String, String> dotenv) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = dotenv.get(name);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing " + name + ". Copy .env.example to .env and fill it in, or set it as an environment variable.");
        }
        return value;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int level = 0; level < 4 && current != null; level++, current = current.getParent()) {
            Path candidate = current.resolve(".env");
            if (!Files.isRegularFile(candidate)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(candidate);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }
                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    values.putIfAbsent(key, value);
                }
                return values;
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read " + candidate, e);
            }
        }
        return values;
    }

    private record Settings(String url, String user, String password) {
    }
}
