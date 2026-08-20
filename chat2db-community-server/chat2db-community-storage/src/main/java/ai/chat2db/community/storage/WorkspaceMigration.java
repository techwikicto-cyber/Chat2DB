package ai.chat2db.community.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Moves the one shared workspace into the workspace of the account that owns it.
 *
 * Before accounts had separate storage there was a single set of directories
 * under {@code storage}, holding whatever the people using the deployment had
 * built between them - in practice the admin's, since that was the only account
 * that existed. Leaving it where it is would mean everyone kept seeing it, which
 * is the thing being fixed; deleting it would throw away real work. So it is
 * handed to the admin, and everyone else starts empty.
 *
 * Runs once. Afterwards {@code storage/users} exists, and its presence is the
 * marker: nothing here writes a flag file that could drift out of step with what
 * is actually on disk.
 */
@Slf4j
public final class WorkspaceMigration {

    /**
     * The directories that make up a workspace.
     *
     * Listed rather than discovered, because {@code storage} also holds things
     * that belong to the installation instead of to a person - {@code user},
     * which is the account file itself, and {@code custom-driver.json}, which is
     * closer to an installed driver than to anyone's data. Sweeping every
     * subdirectory would move the account file into one account's workspace and
     * lock everybody out.
     */
    static final List<String> WORKSPACE_DIRECTORIES = List.of(
            "datasource",
            "namespace",
            "tree",
            "console",
            "operation_log",
            "task",
            "chart",
            "dashboard",
            "er_position",
            "pin_table");

    private WorkspaceMigration() {
    }

    /**
     * Gives the shared workspace to {@code account}, if there is one to give.
     *
     * @return true when data was moved
     */
    public static boolean migrateSharedWorkspaceTo(String account) {
        if (StringUtils.isBlank(account)) {
            return false;
        }
        Path shared = Path.of(WorkspaceScope.sharedBasePath());
        Path users = Path.of(WorkspaceScope.usersBasePath());
        if (Files.exists(users)) {
            return false;  // Already split into per-account workspaces.
        }

        List<Path> present = new ArrayList<>();
        for (String directory : WORKSPACE_DIRECTORIES) {
            Path candidate = shared.resolve(directory);
            if (Files.isDirectory(candidate)) {
                present.add(candidate);
            }
        }
        if (present.isEmpty()) {
            return false;  // Nothing was ever saved; the first sign-in starts clean.
        }

        Path target = Path.of(WorkspaceScope.basePathFor(account));
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            log.error("[chat2db] Could not create the workspace for '{}' at {}. The existing connections and "
                    + "consoles stay shared until this is fixed.", account, target, e);
            return false;
        }

        List<String> moved = new ArrayList<>();
        for (Path directory : present) {
            try {
                Files.move(directory, target.resolve(directory.getFileName()), StandardCopyOption.ATOMIC_MOVE);
                moved.add(directory.getFileName().toString());
            } catch (IOException e) {
                // Stop rather than press on: a half-moved workspace is worse
                // than one that has not started, and what has already moved is
                // named here so it can be put back by hand.
                log.error("[chat2db] Could not move {} into the workspace for '{}'. Moved so far: {}. Nothing was "
                        + "deleted - move these back under {} to undo.", directory.getFileName(), account,
                        moved.isEmpty() ? "nothing" : String.join(", ", moved), shared, e);
                return !moved.isEmpty();
            }
        }

        // Instances built before the move still hold the old contents and point
        // at paths that are no longer there.
        WorkspaceStorages.evictEverything();
        log.info("[chat2db] Gave the existing workspace ({}) to '{}'. Every account now has its own connections, "
                + "consoles and history; nobody sees anyone else's.", String.join(", ", moved), account);
        return true;
    }
}
