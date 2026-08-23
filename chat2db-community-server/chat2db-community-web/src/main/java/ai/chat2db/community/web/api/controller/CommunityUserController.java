package ai.chat2db.community.web.api.controller;

import java.util.List;
import java.util.Optional;

import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.web.api.config.web.auth.CommunityAccessGuard;
import ai.chat2db.community.web.api.config.web.auth.CommunityAuthSupport;
import ai.chat2db.community.web.api.config.web.auth.CommunityPreferencesStore;
import ai.chat2db.community.web.api.config.web.auth.CommunityRole;
import ai.chat2db.community.web.api.config.web.auth.CommunityUser;
import ai.chat2db.community.web.api.config.web.auth.CommunityUserStore;
import ai.chat2db.community.web.api.model.request.user.CommunityUserSaveRequest;
import ai.chat2db.community.web.api.model.response.user.CommunityUserResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account management, for admins.
 *
 * The admin check is not here: {@code CommunityAuthFilter} refuses every path
 * under this mapping to anyone without the role, so a new endpoint added later
 * is covered whether or not its author remembers.
 */
@RestController
@RequestMapping("/api/community/users")
public class CommunityUserController {

    private final CommunityUserStore users;
    private final CommunityAccessGuard guard;
    private final CommunityPreferencesStore preferences;

    public CommunityUserController(CommunityUserStore users, CommunityAccessGuard guard,
            CommunityPreferencesStore preferences) {
        this.users = users;
        this.guard = guard;
        this.preferences = preferences;
    }

    /**
     * Lists every account.
     * <p>
     * Endpoint: {@code GET /api/community/users}.
     *
     * @return data result containing the accounts, without password hashes.
     */
    @GetMapping("")
    public DataResult<List<CommunityUserResponse>> list() {
        return DataResult.of(users.list().stream().map(this::toResponse).toList());
    }

    /**
     * Creates an account.
     * <p>
     * Endpoint: {@code POST /api/community/users}.
     *
     * @param saveRequest request payload describing the account.
     * @param response    the response, used to signal refusal.
     * @return action result for the operation.
     */
    @PostMapping("")
    public ActionResult create(@RequestBody CommunityUserSaveRequest saveRequest, HttpServletResponse response) {
        String username = StringUtils.trimToEmpty(saveRequest == null ? null : saveRequest.getUsername());
        String password = saveRequest == null ? null : saveRequest.getPassword();

        if (!CommunityAuthSupport.isValidUsername(username)) {
            return CommunityAuthSupport.businessFailure("community.user.invalidUsername");
        }
        if (users.find(username).isPresent()) {
            return CommunityAuthSupport.businessFailure("community.user.alreadyExists");
        }
        if (CommunityAuthSupport.isTooShort(password)) {
            return CommunityAuthSupport.businessFailure("community.auth.passwordTooShort");
        }
        users.create(username, password, resolveRole(saveRequest));
        return ActionResult.isSuccess();
    }

    /**
     * Amends an account's role, enabled state or password.
     * <p>
     * Endpoint: {@code PUT /api/community/users/{username}}.
     *
     * @param username    the account to amend.
     * @param saveRequest request payload; a blank password leaves it unchanged.
     * @param response    the response, used to signal refusal.
     * @return action result for the operation.
     */
    @PutMapping("/{username}")
    public ActionResult update(@PathVariable String username, @RequestBody CommunityUserSaveRequest saveRequest,
            HttpServletResponse response) {
        Optional<CommunityUser> existing = users.find(username);
        if (existing.isEmpty()) {
            return CommunityAuthSupport.businessFailure("community.user.notFound");
        }
        boolean disabling = saveRequest != null && Boolean.FALSE.equals(saveRequest.getEnabled());
        boolean demoting = saveRequest != null && StringUtils.isNotBlank(saveRequest.getRole())
                && resolveRole(saveRequest) != CommunityRole.ADMIN;

        // Locking every admin out is unrecoverable without editing files on the
        // host, so the last one cannot be disabled or demoted.
        if ((disabling || demoting) && users.isLastEnabledAdmin(username)) {
            return CommunityAuthSupport.businessFailure("community.user.lastAdmin");
        }
        String password = saveRequest == null ? null : saveRequest.getPassword();
        if (StringUtils.isNotBlank(password)) {
            if (CommunityAuthSupport.isTooShort(password)) {
                return CommunityAuthSupport.businessFailure("community.auth.passwordTooShort");
            }
            users.setPassword(username, password);
            // A reset is usually a response to a compromise; do not leave the old
            // sessions of that account alive behind it.
            guard.revokeSessionsFor(username);
        }
        if (saveRequest != null && StringUtils.isNotBlank(saveRequest.getRole())) {
            users.setRole(username, resolveRole(saveRequest));
        }
        if (saveRequest != null && saveRequest.getEnabled() != null) {
            users.setEnabled(username, saveRequest.getEnabled());
            if (!saveRequest.getEnabled()) {
                guard.revokeSessionsFor(username);
            }
        }
        return ActionResult.isSuccess();
    }

    /**
     * Deletes an account.
     * <p>
     * Endpoint: {@code DELETE /api/community/users/{username}}.
     *
     * @param username the account to delete.
     * @param response the response, used to signal refusal.
     * @return action result for the operation.
     */
    @DeleteMapping("/{username}")
    public ActionResult delete(@PathVariable String username, HttpServletResponse response) {
        if (users.find(username).isEmpty()) {
            return CommunityAuthSupport.businessFailure("community.user.notFound");
        }
        if (users.isLastEnabledAdmin(username)) {
            return CommunityAuthSupport.businessFailure("community.user.lastAdmin");
        }
        users.delete(username);
        guard.revokeSessionsFor(username);
        // The workspace is left alone deliberately - deleting an account should
        // not destroy its work - but the settings are the account's own, and
        // leaving them would hand them to whoever next takes the name.
        preferences.delete(username);
        return ActionResult.isSuccess();
    }

    private CommunityUserResponse toResponse(CommunityUser user) {
        return new CommunityUserResponse(
                user.getUsername(),
                user.getRole() == null ? CommunityRole.USER.name() : user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt(),
                users.isLastEnabledAdmin(user.getUsername()));
    }

    private static CommunityRole resolveRole(CommunityUserSaveRequest request) {
        String role = request == null ? null : StringUtils.trimToEmpty(request.getRole());
        return CommunityRole.ADMIN.name().equalsIgnoreCase(role) ? CommunityRole.ADMIN : CommunityRole.USER;
    }


}
