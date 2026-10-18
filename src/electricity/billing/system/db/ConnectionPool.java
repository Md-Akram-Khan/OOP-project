package electricity.billing.system.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Logger;

/**
 * Keeps a few database connections open and reuses them.
 *
 * Opening a connection to Supabase means a fresh TLS handshake with a server
 * in another country, which takes a second or two. Without reuse, a single
 * click that saves a customer and writes the audit log spent most of its time
 * just connecting. Callers still "close" every connection as before; closing
 * hands it back to the pool instead of hanging up.
 */
final class ConnectionPool {

    @FunctionalInterface
    interface Opener {
        Connection open() throws SQLException;
    }

    private static final Logger LOG = Logger.getLogger(ConnectionPool.class.getName());
    /** Connections kept open while idle. */
    private static final int MAX_IDLE = 6;
    /** A connection idle longer than this is checked before reuse; the server may have dropped it. */
    private static final long CHECK_AFTER_MS = 30_000;
    /** A connection idle longer than this is closed rather than reused. */
    private static final long MAX_IDLE_MS = 5 * 60_000;

    private record Idle(Connection connection, long since) {
    }

    private final Deque<Idle> idle = new ConcurrentLinkedDeque<>();
    private final Opener opener;

    ConnectionPool(Opener opener) {
        this.opener = opener;
    }

    /** A connection to use and then close; closing returns it to the pool. */
    Connection borrow() throws SQLException {
        Idle candidate;
        while ((candidate = idle.pollFirst()) != null) {
            long age = System.currentTimeMillis() - candidate.since();
            Connection raw = candidate.connection();
            if (age <= MAX_IDLE_MS && (age < CHECK_AFTER_MS || isValid(raw))) {
                return wrap(raw);
            }
            closeQuietly(raw);
        }
        return wrap(opener.open());
    }

    private void giveBack(Connection raw) {
        try {
            if (raw.isClosed()) {
                return;
            }
            if (!raw.getAutoCommit()) {
                // Never hand on a half-finished transaction.
                raw.rollback();
                raw.setAutoCommit(true);
            }
            if (idle.size() < MAX_IDLE) {
                idle.addFirst(new Idle(raw, System.currentTimeMillis()));
                return;
            }
        } catch (SQLException e) {
            LOG.fine("Dropping a connection that couldn't be reset: " + e.getMessage());
        }
        closeQuietly(raw);
    }

    /** Closes every idle connection, e.g. when the server stops. */
    void shutdown() {
        Idle candidate;
        while ((candidate = idle.pollFirst()) != null) {
            closeQuietly(candidate.connection());
        }
    }

    private Connection wrap(Connection raw) {
        boolean[] closed = {false};
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "close" -> {
                    if (!closed[0]) {
                        closed[0] = true;
                        giveBack(raw);
                    }
                    return null;
                }
                case "isClosed" -> {
                    return closed[0] || raw.isClosed();
                }
                default -> {
                    if (closed[0]) {
                        throw new SQLException("This connection has already been closed.");
                    }
                    try {
                        return method.invoke(raw, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
        };
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static boolean isValid(Connection c) {
        try {
            return c.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    private static void closeQuietly(Connection c) {
        try {
            c.close();
        } catch (SQLException ignored) {
            // Already gone.
        }
    }
}
