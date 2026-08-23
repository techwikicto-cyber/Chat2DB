package ai.chat2db.community.web.api.config.web.auth;

import java.nio.file.Path;

import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.web.api.controller.CommunityUserController;
import ai.chat2db.community.web.api.model.request.user.CommunityUserSaveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An admin resetting somebody else's password.
 *
 * That is the only way back in for an account whose password is lost, short of
 * editing files on the host, so the behaviour is worth pinning: it must not ask
 * for the old password, it must end that account's sessions, and it must not
 * quietly clear a password when the request is about something else.
 *
 * Lives in the auth package rather than beside the controller so the store's
 * test constructor stays package-private; the controller is public and imports
 * fine from here.
 */
class CommunityUserPasswordResetTest {

    @TempDir
    Path dataDir;

    private CommunityUserStore users;
    private CommunityAccessGuard guard;
    private CommunityPreferencesStore preferences;

    private CommunityUserController controller() {
        users = new CommunityUserStore(dataDir.resolve("user.json"));
        guard = new CommunityAccessGuard(users);
        preferences = new CommunityPreferencesStore(dataDir.resolve("preferences.json"));
        return new CommunityUserController(users, guard, preferences);
    }

    @Test
    void anAdminCanSetAnotherAccountsPasswordWithoutKnowingTheOldOne() {
        CommunityUserController controller = controller();
        users.create("admin", "admin-password", CommunityRole.ADMIN);
        users.create("foad", "forgotten", CommunityRole.USER);

        ActionResult result = controller.update("foad", passwordRequest("a-new-password"), null);

        assertTrue(result.getSuccess(), "the reset was refused");
        assertTrue(guard.authenticate("foad", "a-new-password").isPresent());
        assertTrue(guard.authenticate("foad", "forgotten").isEmpty());
    }

    @Test
    void aResetEndsThatAccountsOpenSessionsAndNobodyElsesAsWell() {
        CommunityUserController controller = controller();
        users.create("foad", "forgotten", CommunityRole.USER);
        users.create("nima", "nima-password", CommunityRole.USER);
        String resetTarget = guard.issueSession("foad");
        String bystander = guard.issueSession("nima");

        controller.update("foad", passwordRequest("a-new-password"), null);

        assertTrue(guard.resolveSession(resetTarget).isEmpty());
        assertTrue(guard.resolveSession(bystander).isPresent(), "an unrelated account was signed out");
    }

    @Test
    void aPasswordBelowTheMinimumIsRefusedAndChangesNothing() {
        CommunityUserController controller = controller();
        users.create("foad", "forgotten", CommunityRole.USER);

        ActionResult result = controller.update("foad", passwordRequest("short"), null);

        assertFalse(result.getSuccess());
        assertEquals("community.auth.passwordTooShort", result.getErrorCode());
        assertTrue(guard.authenticate("foad", "forgotten").isPresent(), "the old password stopped working anyway");
    }

    @Test
    void aRequestCarryingNoPasswordLeavesItAloneRatherThanClearingIt() {
        CommunityUserController controller = controller();
        users.create("foad", "forgotten", CommunityRole.USER);

        // The same endpoint carries role and enabled changes, which must not
        // wipe a password just by not mentioning one.
        CommunityUserSaveRequest request = new CommunityUserSaveRequest();
        request.setRole(CommunityRole.ADMIN.name());
        ActionResult result = controller.update("foad", request, null);

        assertTrue(result.getSuccess());
        assertTrue(guard.authenticate("foad", "forgotten").isPresent());
        assertTrue(users.find("foad").orElseThrow().isAdmin());
    }

    @Test
    void resettingAnAccountThatDoesNotExistSaysSo() {
        CommunityUserController controller = controller();

        ActionResult result = controller.update("nobody", passwordRequest("a-new-password"), null);

        assertFalse(result.getSuccess());
        assertEquals("community.user.notFound", result.getErrorCode());
    }

    private static CommunityUserSaveRequest passwordRequest(String password) {
        CommunityUserSaveRequest request = new CommunityUserSaveRequest();
        request.setPassword(password);
        return request;
    }
}
