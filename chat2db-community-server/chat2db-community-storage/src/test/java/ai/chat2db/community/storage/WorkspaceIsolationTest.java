package ai.chat2db.community.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ai.chat2db.community.domain.api.model.datasource.DataSource;
import ai.chat2db.community.storage.small.DataSourceStorage;
import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.model.LoginUser;
import ai.chat2db.community.tools.util.ContextUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The behaviour the accounts feature was missing: signing in as somebody else
 * shows you their connections, not the ones the admin saved.
 */
class WorkspaceIsolationTest {

    private String originalUserHome;

    @BeforeEach
    void redirectStorage(@TempDir Path home) {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        WorkspaceStorages.evictEverything();
    }

    @AfterEach
    void restore() {
        ContextUtils.removeContext();
        System.setProperty("user.home", originalUserHome);
        WorkspaceStorages.evictEverything();
    }

    @Test
    void oneAccountCannotSeeAnother() {
        signIn("admin");
        DataSourceStorage.current().save(datasource("admin's production database"));

        signIn("reporting");
        assertTrue(DataSourceStorage.current().getDataList().isEmpty(),
                "a new account started with the admin's connections");

        DataSourceStorage.current().save(datasource("reporting's own database"));

        signIn("admin");
        List<DataSource> adminSees = DataSourceStorage.current().getDataList();
        assertEquals(1, adminSees.size());
        assertEquals("admin's production database", adminSees.get(0).getAlias());
    }

    @Test
    void oneAccountCannotFetchAnothersConnectionById() {
        signIn("admin");
        Long id = DataSourceStorage.current().save(datasource("admin's production database"));

        // Hiding a row from a list is not access control - the id is guessable
        // and every database operation takes one. Nothing is filtered here: the
        // row is in a file this account never opens.
        signIn("reporting");
        assertNull(DataSourceStorage.current().getById(id));
    }

    @Test
    void eachAccountGetsItsOwnDirectoryOnDisk() {
        signIn("admin");
        DataSourceStorage.current().save(datasource("somewhere"));
        Path adminFile = Path.of(WorkspaceScope.basePathFor("admin"), "datasource", "datasource.json");

        assertTrue(Files.exists(adminFile), "expected the workspace at " + adminFile);
        assertTrue(adminFile.startsWith(WorkspaceScope.usersBasePath()));
    }

    @Test
    void signedOutWorkKeepsUsingTheSharedWorkspace() {
        // The desktop build and a deployment with sign-in switched off have no
        // accounts at all, and must carry on exactly as before.
        ContextUtils.removeContext();
        DataSourceStorage.current().save(datasource("desktop connection"));

        assertTrue(Files.exists(Path.of(WorkspaceScope.sharedBasePath(), "datasource", "datasource.json")));
        assertEquals(1, DataSourceStorage.current().getDataList().size());
    }

    private static DataSource datasource(String alias) {
        DataSource dataSource = new DataSource();
        dataSource.setAlias(alias);
        dataSource.setType("MYSQL");
        return dataSource;
    }

    private static void signIn(String account) {
        LoginUser user = new LoginUser();
        user.setAccountName(account);
        ContextUtils.setContext(Context.builder().loginUser(user).build());
    }
}
