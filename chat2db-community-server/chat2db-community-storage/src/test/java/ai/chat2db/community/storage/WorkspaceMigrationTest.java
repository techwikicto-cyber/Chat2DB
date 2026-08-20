package ai.chat2db.community.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceMigrationTest {

    private String originalUserHome;

    @BeforeEach
    void redirectStorage(@TempDir Path home) {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        WorkspaceStorages.evictEverything();
    }

    @AfterEach
    void restore() {
        System.setProperty("user.home", originalUserHome);
        WorkspaceStorages.evictEverything();
    }

    @Test
    void theExistingWorkspaceBecomesTheAdmins() throws IOException {
        write("datasource/datasource.json", "{\"id\":1,\"alias\":\"production\"}");
        write("console/console.json", "1");

        assertTrue(WorkspaceMigration.migrateSharedWorkspaceTo("admin"));

        Path admin = Path.of(WorkspaceScope.basePathFor("admin"));
        assertEquals("{\"id\":1,\"alias\":\"production\"}",
                Files.readString(admin.resolve("datasource/datasource.json"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(admin.resolve("console/console.json")));
        assertFalse(Files.exists(shared().resolve("datasource")), "the shared copy should have moved, not been copied");
    }

    @Test
    void theAccountFileAndInstalledDriversStayWhereTheyAre() throws IOException {
        write("datasource/datasource.json", "{\"id\":1}");
        // Sits in the same directory and is one character away from the "users"
        // directory the workspaces live in. Moving it into one account's
        // workspace would lock every account out.
        write("user/user.json", "{\"username\":\"admin\"}");
        write("custom-driver.json", "{}");

        WorkspaceMigration.migrateSharedWorkspaceTo("admin");

        assertTrue(Files.exists(shared().resolve("user/user.json")), "the account file must not move");
        assertTrue(Files.exists(shared().resolve("custom-driver.json")), "installed drivers belong to the machine");
    }

    @Test
    void migrationHappensOnceAndOnlyOnce() throws IOException {
        write("datasource/datasource.json", "{\"id\":1}");
        assertTrue(WorkspaceMigration.migrateSharedWorkspaceTo("admin"));

        // Somebody creates a connection in the shared area afterwards - a
        // desktop-mode start, say. It must not be swept into an account.
        write("datasource/datasource.json", "{\"id\":2}");

        assertFalse(WorkspaceMigration.migrateSharedWorkspaceTo("admin"));
        assertEquals("{\"id\":2}",
                Files.readString(shared().resolve("datasource/datasource.json"), StandardCharsets.UTF_8));
    }

    @Test
    void aFreshInstallHasNothingToMigrate() {
        assertFalse(WorkspaceMigration.migrateSharedWorkspaceTo("admin"));
        assertFalse(Files.exists(Path.of(WorkspaceScope.usersBasePath())));
    }

    @Test
    void withoutAnAccountNothingIsTouched() throws IOException {
        write("datasource/datasource.json", "{\"id\":1}");

        assertFalse(WorkspaceMigration.migrateSharedWorkspaceTo(null));
        assertFalse(WorkspaceMigration.migrateSharedWorkspaceTo("  "));
        assertTrue(Files.exists(shared().resolve("datasource/datasource.json")));
    }

    private static Path shared() {
        return Path.of(WorkspaceScope.sharedBasePath());
    }

    private static void write(String relative, String content) throws IOException {
        Path target = shared().resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
