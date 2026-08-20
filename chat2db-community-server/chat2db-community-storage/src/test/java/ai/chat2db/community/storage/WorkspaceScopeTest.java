package ai.chat2db.community.storage;

import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.model.LoginUser;
import ai.chat2db.community.tools.util.ContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceScopeTest {

    @AfterEach
    void clearContext() {
        ContextUtils.removeContext();
    }

    @Test
    void withoutAnAccountTheSharedWorkspaceIsUsed() {
        assertNull(WorkspaceScope.currentAccount());
        assertEquals(WorkspaceScope.sharedBasePath(), WorkspaceScope.currentBasePath());
    }

    @Test
    void theSignedInAccountDecidesTheWorkspace() {
        signIn("alice");

        assertEquals("alice", WorkspaceScope.currentAccount());
        assertEquals(WorkspaceScope.basePathFor("alice"), WorkspaceScope.currentBasePath());
        assertNotEquals(WorkspaceScope.sharedBasePath(), WorkspaceScope.currentBasePath());
    }

    @Test
    void twoAccountsNeverShareADirectory() {
        assertNotEquals(WorkspaceScope.basePathFor("alice"), WorkspaceScope.basePathFor("bob"));
        // The readable part of both of these reduces to "a_b"; only the hash,
        // which is taken from the original name, keeps them apart.
        assertNotEquals(WorkspaceScope.directoryName("a b"), WorkspaceScope.directoryName("a.b"));
    }

    @Test
    void theSameAccountAlwaysLandsInOneDirectoryHoweverItIsSpelled() {
        // The account store matches usernames case-insensitively, so the
        // directory has to agree or signing in as "Ali" would open an empty
        // workspace belonging to nobody.
        assertEquals(WorkspaceScope.directoryName("ali"), WorkspaceScope.directoryName("Ali"));
        assertEquals(WorkspaceScope.directoryName("ali"), WorkspaceScope.directoryName("  ALI  "));
    }

    @Test
    void aCraftedUsernameCannotClimbOutOfTheWorkspace() {
        for (String hostile : new String[] {"../../etc", "..", "a/../../b", "C:\\windows", "x\u0000y"}) {
            String directory = WorkspaceScope.directoryName(hostile);
            assertTrue(directory.matches("[a-z0-9._-]+"), hostile + " produced " + directory);
            assertTrue(WorkspaceScope.basePathFor(hostile).startsWith(WorkspaceScope.usersBasePath()));
        }
    }

    @Test
    void anAccountOfOnlyUnsafeCharactersStillGetsItsOwnDirectory() {
        assertNotEquals(WorkspaceScope.directoryName("///"), WorkspaceScope.directoryName("..."));
    }

    private static void signIn(String account) {
        LoginUser user = new LoginUser();
        user.setAccountName(account);
        ContextUtils.setContext(Context.builder().loginUser(user).build());
    }
}
