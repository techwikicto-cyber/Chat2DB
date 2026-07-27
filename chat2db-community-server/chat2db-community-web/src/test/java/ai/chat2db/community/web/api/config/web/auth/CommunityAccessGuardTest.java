package ai.chat2db.community.web.api.config.web.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityAccessGuardTest {

    private static final String PASSWORD_PROPERTY = "chat2db.community.password";

    @AfterEach
    void clearPassword() {
        System.clearProperty(PASSWORD_PROPERTY);
    }

    @Test
    void noPasswordConfiguredLeavesTheGateOpen() {
        // The default, and what every existing installation and the desktop
        // build rely on.
        CommunityAccessGuard guard = new CommunityAccessGuard();
        assertFalse(guard.isEnabled());
        assertFalse(guard.matches(""));
        assertFalse(guard.matches("anything"));
    }

    @Test
    void blankPasswordIsNotAPassword() {
        System.setProperty(PASSWORD_PROPERTY, "   ");
        assertFalse(new CommunityAccessGuard().isEnabled());
    }

    @Test
    void acceptsOnlyTheConfiguredPassword() {
        System.setProperty(PASSWORD_PROPERTY, "correct horse");
        CommunityAccessGuard guard = new CommunityAccessGuard();

        assertTrue(guard.isEnabled());
        assertTrue(guard.matches("correct horse"));
        assertFalse(guard.matches("correct hors"));
        assertFalse(guard.matches("Correct Horse"));
        assertFalse(guard.matches(null));
    }

    @Test
    void issuesDistinctSessionsAndValidatesThem() {
        System.setProperty(PASSWORD_PROPERTY, "secret");
        CommunityAccessGuard guard = new CommunityAccessGuard();

        String first = guard.issueSession();
        String second = guard.issueSession();

        assertNotEquals(first, second);
        assertTrue(guard.isValidSession(first));
        assertTrue(guard.isValidSession(second));

        assertFalse(guard.isValidSession(null));
        assertFalse(guard.isValidSession(""));
        assertFalse(guard.isValidSession("forged"));
    }

    @Test
    void revokingASessionEndsIt() {
        System.setProperty(PASSWORD_PROPERTY, "secret");
        CommunityAccessGuard guard = new CommunityAccessGuard();

        String token = guard.issueSession();
        guard.revokeSession(token);

        assertFalse(guard.isValidSession(token));
        assertEquals(0, guard.sessionCount());
    }

    @Test
    void guardsTheApiButNotTheWayIn() {
        // Leaving the sign-in endpoints open is the point: they are how a
        // session is obtained.
        assertTrue(CommunityAuthFilter.isGuardedPath("/api/v3/ai/chat/stream", ""));
        assertTrue(CommunityAuthFilter.isGuardedPath("/app/api/connection/list", "/app"));

        assertFalse(CommunityAuthFilter.isGuardedPath("/api/community/auth/login", ""));
        assertFalse(CommunityAuthFilter.isGuardedPath("/api/community/auth/status", ""));

        // The shell and its assets are not the API; the gate is on the data.
        assertFalse(CommunityAuthFilter.isGuardedPath("/", ""));
        assertFalse(CommunityAuthFilter.isGuardedPath("/static/front/umi.js", ""));
        assertFalse(CommunityAuthFilter.isGuardedPath(null, ""));
    }
}
