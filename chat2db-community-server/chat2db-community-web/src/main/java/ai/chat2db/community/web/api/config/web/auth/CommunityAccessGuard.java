package ai.chat2db.community.web.api.config.web.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Shared-password access control for the Community web deployment.
 *
 * Chat2DB Community has no user accounts, and every saved datasource password
 * and AI key is readable by anyone who can reach the port. That is defensible on
 * a desktop and indefensible on a server, so this puts a single shared password
 * in front of the whole API.
 *
 * It is a gate, not an identity system: everyone who signs in is the same
 * anonymous operator, and nothing is scoped per user. Deployments that need to
 * tell people apart need real accounts, which this deliberately is not.
 *
 * Configuring no password leaves the gate open, which keeps existing
 * installations and the desktop build working exactly as before.
 */
@Component
public class CommunityAccessGuard {

    /** Session cookie name. Prefixed like the storage keys so it is recognisable. */
    public static final String SESSION_COOKIE = "chat2db_community_session";

    private static final String PASSWORD_PROPERTY = "chat2db.community.password";
    private static final String PASSWORD_ENV = "CHAT2DB_COMMUNITY_PASSWORD";

    /** Long enough not to be a daily annoyance, short enough to expire eventually. */
    public static final Duration SESSION_TTL = Duration.ofDays(7);

    /**
     * Sessions live in memory only. A restart signs everybody out, which is the
     * right trade for a single-container deployment: no shared secret to persist
     * and no stale token that outlives a password change.
     */
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Whether a password is configured at all. No password means no gate. */
    public boolean isEnabled() {
        return StringUtils.isNotBlank(configuredPassword());
    }

    /** Constant-time comparison, so a wrong guess reveals nothing by timing. */
    public boolean matches(String candidate) {
        String expected = configuredPassword();
        if (StringUtils.isBlank(expected) || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

    /** Creates a session and returns its token. */
    public String issueSession() {
        byte[] token = new byte[32];
        random.nextBytes(token);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        sessions.put(value, Instant.now().plus(SESSION_TTL));
        return value;
    }

    public boolean isValidSession(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        Instant expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void revokeSession(String token) {
        if (StringUtils.isNotBlank(token)) {
            sessions.remove(token);
        }
    }

    /**
     * Drops expired entries. Called on sign-in rather than on a timer: sessions
     * are few, and an abandoned one costs a map entry until the next sign-in.
     */
    void pruneExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> entries = sessions.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().isBefore(now)) {
                entries.remove();
            }
        }
    }

    int sessionCount() {
        return sessions.size();
    }

    private String configuredPassword() {
        String configured = System.getProperty(PASSWORD_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            configured = System.getenv(PASSWORD_ENV);
        }
        return configured;
    }
}
