package ai.chat2db.community.tools.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import ai.chat2db.community.tools.model.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numeric id everything user-scoped is keyed by.
 *
 * It used to be the same constant for every request, which is why one account's
 * AI model configuration - API key included - was every account's.
 */
class CommunityIdentityTest {

    @AfterEach
    void clearAccount() {
        CommunityAccountContext.clear();
        ContextUtils.removeContext();
    }

    @Test
    void withoutAnAccountTheSharedIdIsUsed() {
        assertEquals(CommunityIdentity.USER_ID, CommunityIdentity.userId());
    }

    @Test
    void eachAccountGetsItsOwnId() {
        CommunityAccountContext.set("admin");
        long admin = CommunityIdentity.userId();
        CommunityAccountContext.set("foad");
        long foad = CommunityIdentity.userId();

        assertNotEquals(admin, foad);
        assertNotEquals(CommunityIdentity.USER_ID, admin);
        assertNotEquals(CommunityIdentity.USER_ID, foad);
    }

    @Test
    void theSameAccountAlwaysGetsTheSameId() {
        // Derived rather than allocated, so it survives a restart and is the
        // same in a second container reading the same data.
        assertEquals(CommunityIdentity.accountUserId("foad"), CommunityIdentity.accountUserId("foad"));
        // Accounts are matched case-insensitively; the id has to agree.
        assertEquals(CommunityIdentity.accountUserId("foad"), CommunityIdentity.accountUserId("Foad"));
        assertEquals(CommunityIdentity.accountUserId("foad"), CommunityIdentity.accountUserId("  FOAD  "));
    }

    @Test
    void idsArePositiveSoTheyCannotCollideWithTheNoAccountId() {
        for (String account : new String[] {"a", "admin", "foad", "nima", "ali", "z".repeat(64)}) {
            assertTrue(CommunityIdentity.accountUserId(account) >= 0, account);
        }
    }

    @Test
    void workHandedToAnotherThreadKeepsTheAccount() throws Exception {
        // The thread local belongs to the request thread. Everything handed off -
        // the assistant calling a tool, an export, the SQL executor - carries the
        // Context instead, and reading only the thread local there fell back to
        // the shared id: the account's AI model configuration was then looked up
        // under the wrong key and came back empty.
        CommunityAccountContext.set("foad");
        Context captured = CommunityIdentity.context();
        long onRequestThread = CommunityIdentity.userId();

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Long> onWorkerThread = worker.submit(() -> {
                ContextUtils.setContext(captured);
                try {
                    return CommunityIdentity.userId();
                } finally {
                    ContextUtils.removeContext();
                }
            });
            assertEquals(onRequestThread, onWorkerThread.get());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void aThreadWithNeitherFallsBackToTheSharedId() throws Exception {
        CommunityAccountContext.set("foad");

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            assertEquals(CommunityIdentity.USER_ID, worker.submit(CommunityIdentity::userId).get());
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void theLoginUserCarriesBothTheIdAndTheName() {
        CommunityAccountContext.set("foad");

        assertEquals(CommunityIdentity.accountUserId("foad"), CommunityIdentity.loginUser().getId());
        assertEquals("foad", CommunityIdentity.loginUser().getAccountName());
    }
}
