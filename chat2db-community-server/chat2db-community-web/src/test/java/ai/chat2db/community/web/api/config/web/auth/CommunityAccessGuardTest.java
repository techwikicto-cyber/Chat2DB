package ai.chat2db.community.web.api.config.web.auth;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityAccessGuardTest {

    private static final String PASSWORD_PROPERTY = "chat2db.community.password";
    private static final String DISABLE_PROPERTY = "chat2db.community.disable-login";

    @TempDir
    Path dataDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty(PASSWORD_PROPERTY);
        System.clearProperty(DISABLE_PROPERTY);
    }

    private CommunityUserStore store() {
        return new CommunityUserStore(dataDir.resolve("user.json"));
    }

    @Test
    void passwordsAreSaltedSoTwoAccountsSharingOneDoNotLookAlike() {
        CommunityUserStore users = store();
        users.create("ali", "correct horse", CommunityRole.ADMIN);
        users.create("sara", "correct horse", CommunityRole.USER);

        String first = users.find("ali").orElseThrow().getPasswordHash();
        String second = users.find("sara").orElseThrow().getPasswordHash();

        assertNotEquals(first, second);
        assertFalse(first.contains("correct horse"));
        assertTrue(PasswordHasher.verify("correct horse", first));
        assertFalse(PasswordHasher.verify("Correct Horse", first));
        assertFalse(PasswordHasher.verify("correct hors", first));
    }

    @Test
    void malformedStoredHashesAreRejectedRatherThanThrowing() {
        assertFalse(PasswordHasher.verify("anything", ""));
        assertFalse(PasswordHasher.verify("anything", "not-a-hash"));
        assertFalse(PasswordHasher.verify("anything", "pbkdf2$abc$def$ghi"));
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("x")));
    }

    @Test
    void firstStartCreatesAnAdminFromTheConfiguredPassword() {
        System.setProperty(PASSWORD_PROPERTY, "bootstrap-secret");
        CommunityUserStore users = store();
        CommunityAccessGuard guard = new CommunityAccessGuard(users);
        guard.bootstrap();

        CommunityUser admin = users.find(CommunityAccessGuard.BOOTSTRAP_USERNAME).orElseThrow();
        assertTrue(admin.isAdmin());
        assertTrue(admin.isEnabled());
        assertTrue(guard.authenticate(CommunityAccessGuard.BOOTSTRAP_USERNAME, "bootstrap-secret").isPresent());

        // Restarting must not reset the admin's password to the bootstrap value.
        users.setPassword(CommunityAccessGuard.BOOTSTRAP_USERNAME, "changed-later");
        guard.bootstrap();
        assertTrue(guard.authenticate(CommunityAccessGuard.BOOTSTRAP_USERNAME, "changed-later").isPresent());
        assertFalse(guard.authenticate(CommunityAccessGuard.BOOTSTRAP_USERNAME, "bootstrap-secret").isPresent());
    }

    @Test
    void refusesWrongPasswordsUnknownAccountsAndDisabledOnes() {
        CommunityUserStore users = store();
        users.create("ali", "hunter2-long", CommunityRole.USER);
        CommunityAccessGuard guard = new CommunityAccessGuard(users);

        assertTrue(guard.authenticate("ali", "hunter2-long").isPresent());
        assertTrue(guard.authenticate("ALI", "hunter2-long").isPresent(), "usernames are case-insensitive");
        assertFalse(guard.authenticate("ali", "wrong").isPresent());
        assertFalse(guard.authenticate("nobody", "hunter2-long").isPresent());

        users.setEnabled("ali", false);
        assertFalse(guard.authenticate("ali", "hunter2-long").isPresent());
    }

    @Test
    void disablingAnAccountEndsItsSessionsAtOnce() {
        CommunityUserStore users = store();
        users.create("ali", "hunter2-long", CommunityRole.USER);
        CommunityAccessGuard guard = new CommunityAccessGuard(users);

        String token = guard.issueSession("ali");
        assertTrue(guard.isValidSession(token));

        // No revocation call: the session resolves against the store on every
        // request, so the change lands without waiting for the next sign-in.
        users.setEnabled("ali", false);
        assertFalse(guard.isValidSession(token));
        assertEquals(0, guard.sessionCount(), "the dead session is dropped, not merely refused");
    }

    @Test
    void sessionsAreDistinctAndRevocable() {
        CommunityUserStore users = store();
        users.create("ali", "hunter2-long", CommunityRole.USER);
        CommunityAccessGuard guard = new CommunityAccessGuard(users);

        String first = guard.issueSession("ali");
        String second = guard.issueSession("ali");
        assertNotEquals(first, second);

        guard.revokeSession(first);
        assertFalse(guard.isValidSession(first));
        assertTrue(guard.isValidSession(second));

        guard.revokeSessionsFor("ALI");
        assertFalse(guard.isValidSession(second), "revocation by name is case-insensitive");
    }

    @Test
    void theLastEnabledAdminIsProtected() {
        CommunityUserStore users = store();
        users.create("admin", "hunter2-long", CommunityRole.ADMIN);
        users.create("ali", "hunter2-long", CommunityRole.USER);

        assertTrue(users.isLastEnabledAdmin("admin"));
        assertFalse(users.isLastEnabledAdmin("ali"));

        users.create("second-admin", "hunter2-long", CommunityRole.ADMIN);
        assertFalse(users.isLastEnabledAdmin("admin"), "no longer the last one");

        users.setEnabled("second-admin", false);
        assertTrue(users.isLastEnabledAdmin("admin"), "a disabled admin does not count");
    }

    @Test
    void accountsSurviveARestart() throws Exception {
        Path file = dataDir.resolve("user.json");
        CommunityUserStore users = new CommunityUserStore(file);
        users.create("ali", "hunter2-long", CommunityRole.ADMIN);

        CommunityUserStore reloaded = new CommunityUserStore(file);
        Optional<CommunityUser> ali = reloaded.find("ali");
        assertTrue(ali.isPresent());
        assertTrue(ali.get().isAdmin());
        assertTrue(PasswordHasher.verify("hunter2-long", ali.get().getPasswordHash()));

        assertFalse(Files.readString(file).contains("hunter2-long"), "the file must not hold the password itself");
    }

    @Test
    void signInCanBeSwitchedOffOutright() {
        System.setProperty(DISABLE_PROPERTY, "true");
        assertFalse(new CommunityAccessGuard(store()).isEnabled());
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

    @Test
    void accountManagementIsAdminOnly() {
        assertTrue(CommunityAuthFilter.isAdminPath("/api/community/users", ""));
        assertTrue(CommunityAuthFilter.isAdminPath("/api/community/users/ali", ""));
        assertTrue(CommunityAuthFilter.isAdminPath("/app/api/community/users", "/app"));

        assertFalse(CommunityAuthFilter.isAdminPath("/api/v3/ai/chat/stream", ""));
        assertFalse(CommunityAuthFilter.isAdminPath("/api/community/auth/status", ""));
    }

    @Test
    void usernamesAreNarrowlyConstrained() {
        assertTrue(CommunityAuthSupport.isValidUsername("ali"));
        assertTrue(CommunityAuthSupport.isValidUsername("ali.reza_2"));
        assertTrue(CommunityAuthSupport.isValidUsername("A-B"));

        assertFalse(CommunityAuthSupport.isValidUsername("a"), "too short");
        assertFalse(CommunityAuthSupport.isValidUsername("a".repeat(33)), "too long");
        assertFalse(CommunityAuthSupport.isValidUsername("ali reza"), "no spaces");
        assertFalse(CommunityAuthSupport.isValidUsername("../etc/passwd"));
        assertFalse(CommunityAuthSupport.isValidUsername(null));
    }
}
