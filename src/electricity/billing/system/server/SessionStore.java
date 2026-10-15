package electricity.billing.system.server;

import electricity.billing.system.model.UserAccount;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logged-in users, keyed by a random bearer token. Tokens live in memory, so
 * restarting the server logs everyone out.
 */
final class SessionStore {

    static final Duration LIFETIME = Duration.ofHours(8);
    private static final int TOKEN_BYTES = 32;

    private record Session(UserAccount account, Instant expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    SessionStore(Clock clock) {
        this.clock = clock;
    }

    String create(UserAccount account) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(account, clock.instant().plus(LIFETIME)));
        removeExpired();
        return token;
    }

    Optional<UserAccount> find(String token) {
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (clock.instant().isAfter(session.expiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.account());
    }

    void remove(String token) {
        sessions.remove(token);
    }

    private void removeExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(s -> now.isAfter(s.expiresAt()));
    }
}
