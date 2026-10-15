package electricity.billing.system.server;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Locks a username for a while after too many wrong passwords, to stop password guessing. */
final class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    void checkAllowed(String username) {
        Attempts a = attempts.get(key(username));
        if (a != null && a.lockedUntil() != null && clock.instant().isBefore(a.lockedUntil())) {
            long minutes = Math.max(1, Duration.between(clock.instant(), a.lockedUntil()).toMinutes());
            throw ApiError.tooManyRequests(
                    "Too many failed logins. Try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
    }

    void recordFailure(String username) {
        Instant now = clock.instant();
        attempts.compute(key(username), (k, a) -> {
            if (a == null || now.isAfter(a.windowStart().plus(WINDOW))) {
                a = new Attempts(0, now, null);
            }
            int failures = a.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? now.plus(LOCKOUT) : null;
            return new Attempts(failures, a.windowStart(), lockedUntil);
        });
    }

    void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
