package ai.chat2db.community.start.workspace;

import java.util.Comparator;
import java.util.Optional;

import ai.chat2db.community.storage.WorkspaceMigration;
import ai.chat2db.community.tools.util.ConfigUtils;
import ai.chat2db.community.web.api.config.web.auth.CommunityAccessGuard;
import ai.chat2db.community.web.api.config.web.auth.CommunityUser;
import ai.chat2db.community.web.api.config.web.auth.CommunityUserStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hands the pre-accounts workspace to the admin on the first start after the
 * upgrade.
 *
 * Lives here rather than beside either half it touches: accounts are the web
 * module's, workspaces are the storage module's, and neither depends on the
 * other. This module already assembles both.
 *
 * Depends on {@link CommunityAccessGuard} so that its own start-up has finished
 * first - the bootstrap admin is created there, and on a fresh install it would
 * not exist yet to migrate to.
 */
@Slf4j
@Component
public class WorkspaceMigrationStarter {

    private final CommunityAccessGuard guard;
    private final CommunityUserStore users;

    public WorkspaceMigrationStarter(CommunityAccessGuard guard, CommunityUserStore users) {
        this.guard = guard;
        this.users = users;
    }

    @PostConstruct
    void migrate() {
        if (!ConfigUtils.isCommunity()) {
            return;
        }
        if (!guard.isEnabled()) {
            // Sign-in is switched off, so nobody is ever signed in and every
            // request reads the shared workspace. Moving it under an account
            // would hide it from the very deployment still using it - accounts
            // left over from before the switch make this reachable, not
            // hypothetical.
            return;
        }
        Optional<String> admin = owningAdmin();
        if (admin.isEmpty()) {
            // No accounts: either sign-in is switched off, or the bootstrap
            // password never arrived. Both leave one shared workspace, which is
            // what a deployment without accounts should have.
            return;
        }
        WorkspaceMigration.migrateSharedWorkspaceTo(admin.get());
    }

    /**
     * Who inherits the existing data.
     *
     * The enabled admin, and on the first upgrade there is exactly one. More
     * than one is possible if accounts were added before upgrading, and then the
     * choice is arbitrary - so it is made the same way every time, by name, and
     * said out loud rather than picked silently.
     */
    private Optional<String> owningAdmin() {
        var admins = users.list().stream()
                .filter(CommunityUser::isEnabled)
                .filter(CommunityUser::isAdmin)
                .map(CommunityUser::getUsername)
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
        if (admins.isEmpty()) {
            return Optional.empty();
        }
        if (admins.size() > 1) {
            log.warn("[chat2db] {} admin accounts exist, so the workspace that was shared until now goes to '{}', "
                    + "the first by name. The others start with an empty workspace.", admins.size(), admins.get(0));
        }
        return Optional.of(admins.get(0));
    }
}
