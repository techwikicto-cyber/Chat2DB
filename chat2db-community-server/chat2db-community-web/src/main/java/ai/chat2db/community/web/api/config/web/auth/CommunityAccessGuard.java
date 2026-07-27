package ai.chat2db.community.web.api.config.web.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Sign-in for the Community web deployment.
 *
 * Chat2DB Community has no accounts of its own, and every saved datasource
 * password and AI key is readable by whoever can reach the port. That is fine on
 * a desktop and not fine on a server, so this puts named accounts in front of
 * the whole API.
 *
 * What an account is and is not: everyone shares the same datasources, consoles
 * and history, because nothing in the storage model is scoped per user. Accounts
 * buy individual credentials, revoking one person without disturbing the rest,
 * and a name against each operation. They do not separate anyone's data from
 * anyone else's.
 *
 * The first start creates an {@code admin} account from
 * CHAT2DB_COMMUNITY_PASSWORD - which the container entrypoint generates and
 * prints when the operator has not set one - so there is always a way in and
 * never a step to get wrong.
 */
@Slf4j
@Component
public class CommunityAccessGuard {

    /** Session cookie name. Prefixed like the storage keys so it is recognisable. */
    public static final String SESSION_COOKIE = "chat2db_community_session";

    /** The account the first start creates. */
    public static final String BOOTSTRAP_USERNAME = "admin";

    private static final String PASSWORD_PROPERTY = "chat2db.community.password";
    private static final String PASSWORD_ENV = "CHAT2DB_COMMUNITY_PASSWORD";
    private static final String DISABLE_PROPERTY = "chat2db.community.disable-login";
    private static final String DISABLE_ENV = "CHAT2DB_DISABLE_LOGIN";

    /** Long enough not to be a daily annoyance, short enough to expire eventually. */
    public static final Duration SESSION_TTL = Duration.ofDays(7);

    /**
     * Sessions live in memory only. A restart signs everybody out, which is the
     * right trade for a single-container deployment: no shared secret to persist
     * and no stale token that outlives a password change.
     */
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CommunityUserStore users;

    public CommunityAccessGuard(CommunityUserStore users) {
        this.users = users;
    }

    private record Session(String username, Instant expiresAt) {
    }

    @PostConstruct
    void bootstrap() {
        if (isDisabled()) {
            log.warn("[chat2db] Sign-in is disabled: anyone who can reach this port has full access to every "
                    + "stored connection. Unset CHAT2DB_DISABLE_LOGIN to require accounts.");
            return;
        }
        if (users.isEmpty()) {
            String password = configuredPassword();
            if (StringUtils.isBlank(password)) {
                log.error("[chat2db] No accounts exist and CHAT2DB_COMMUNITY_PASSWORD is not set, so no admin "
                        + "account could be created. Set it and restart, or nobody can sign in.");
                return;
            }
            users.create(BOOTSTRAP_USERNAME, password, CommunityRole.ADMIN);
            log.info("[chat2db] Created the '{}' account from CHAT2DB_COMMUNITY_PASSWORD. Sign in with it, then "
                    + "add accounts for everyone else in Settings.", BOOTSTRAP_USERNAME);
        }
        log.info("[chat2db] Sign-in is ENABLED with {} account(s). Sessions expire after {} days.",
                users.list().size(), SESSION_TTL.toDays());
    }

    /** Whether the gate is up at all. */
    public boolean isEnabled() {
        return !isDisabled();
    }

    /**
     * Checks a username and password.
     *
     * A disabled account and a wrong password are answered identically, so the
     * screen cannot be used to learn which accounts exist.
     */
    public Optional<CommunityUser> authenticate(String username, String password) {
        Optional<CommunityUser> candidate = users.find(username);
        // Verify even when the account is missing or disabled, so the reply takes
        // the same time either way and does not leak which usernames are real.
        String storedHash = candidate.map(CommunityUser::getPasswordHash).orElse("");
        boolean passwordMatches = PasswordHasher.verify(password, storedHash);
        if (!passwordMatches || candidate.isEmpty() || !candidate.get().isEnabled()) {
            return Optional.empty();
        }
        return candidate;
    }

    /** Creates a session for an authenticated account and returns its token. */
    public String issueSession(String username) {
        pruneExpired();
        byte[] token = new byte[32];
        random.nextBytes(token);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        sessions.put(value, new Session(username, Instant.now().plus(SESSION_TTL)));
        return value;
    }

    /**
     * The account behind a session token, if it is still valid.
     *
     * Re-read from the store on every request rather than trusted from the
     * session, so disabling an account or changing its role takes effect at once
     * instead of whenever that person next signs in.
     */
    public Optional<CommunityUser> resolveSession(String token) {
        if (StringUtils.isBlank(token)) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        Optional<CommunityUser> user = users.find(session.username());
        if (user.isEmpty() || !user.get().isEnabled()) {
            sessions.remove(token);
            return Optional.empty();
        }
        return user;
    }

    public boolean isValidSession(String token) {
        return resolveSession(token).isPresent();
    }

    public void revokeSession(String token) {
        if (StringUtils.isNotBlank(token)) {
            sessions.remove(token);
        }
    }

    /** Ends every session belonging to an account, used when it is disabled or deleted. */
    public void revokeSessionsFor(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        sessions.entrySet().removeIf(entry -> entry.getValue().username().equalsIgnoreCase(username.trim()));
    }

    void pruneExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Session>> entries = sessions.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().expiresAt().isBefore(now)) {
                entries.remove();
            }
        }
    }

    int sessionCount() {
        return sessions.size();
    }

    private boolean isDisabled() {
        String configured = System.getProperty(DISABLE_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            configured = System.getenv(DISABLE_ENV);
        }
        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(configured));
    }

    private String configuredPassword() {
        String configured = System.getProperty(PASSWORD_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            configured = System.getenv(PASSWORD_ENV);
        }
        return configured;
    }
}
