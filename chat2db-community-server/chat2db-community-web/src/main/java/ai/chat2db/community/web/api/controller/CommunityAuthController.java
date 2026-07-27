package ai.chat2db.community.web.api.controller;

import java.time.Duration;
import java.util.Optional;

import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.web.api.config.web.auth.CommunityAccessGuard;
import ai.chat2db.community.web.api.config.web.auth.CommunityAuthSupport;
import ai.chat2db.community.web.api.config.web.auth.CommunityUser;
import ai.chat2db.community.web.api.config.web.auth.CommunityUserStore;
import ai.chat2db.community.web.api.model.request.auth.CommunityLoginRequest;
import ai.chat2db.community.web.api.model.request.auth.CommunityPasswordChangeRequest;
import ai.chat2db.community.web.api.model.response.auth.CommunityAuthStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signing in, out, and changing one's own password.
 *
 * Reachable without a session - see {@code CommunityAuthFilter} - because this
 * is how a session is obtained in the first place.
 */
@RestController
@RequestMapping("/api/community/auth")
public class CommunityAuthController {

    private final CommunityAccessGuard guard;
    private final CommunityUserStore users;

    public CommunityAuthController(CommunityAccessGuard guard, CommunityUserStore users) {
        this.guard = guard;
        this.users = users;
    }

    /**
     * Reports whether sign-in is required and who this browser is signed in as.
     * <p>
     * Endpoint: {@code GET /api/community/auth/status}.
     *
     * @param request the incoming request, read for its session cookie.
     * @return data result describing what the interface should show.
     */
    @GetMapping("/status")
    public DataResult<CommunityAuthStatusResponse> status(HttpServletRequest request) {
        boolean required = guard.isEnabled();
        if (!required) {
            return DataResult.of(new CommunityAuthStatusResponse(false, true, null, null));
        }
        Optional<CommunityUser> user = guard.resolveSession(CommunityAuthSupport.sessionCookie(request));
        return DataResult.of(user
                .map(value -> new CommunityAuthStatusResponse(true, true, value.getUsername(), value.getRole().name()))
                .orElseGet(() -> new CommunityAuthStatusResponse(true, false, null, null)));
    }

    /**
     * Exchanges a username and password for a session cookie.
     * <p>
     * Endpoint: {@code POST /api/community/auth/login}.
     *
     * @param loginRequest request payload carrying the credentials.
     * @param request      the incoming request, read to decide cookie flags.
     * @param response     the response the session cookie is written to.
     * @return action result; unsuccessful with HTTP 401 when the credentials are wrong.
     */
    @PostMapping("/login")
    public ActionResult login(@RequestBody CommunityLoginRequest loginRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (!guard.isEnabled()) {
            // Nothing to sign in to. Say so rather than minting a useless cookie.
            return ActionResult.isSuccess();
        }
        String username = loginRequest == null ? null : loginRequest.getUsername();
        String password = loginRequest == null ? null : loginRequest.getPassword();

        Optional<CommunityUser> user = guard.authenticate(username, password);
        if (user.isEmpty()) {
            return CommunityAuthSupport.failure(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "community.auth.invalidCredentials");
        }
        CommunityAuthSupport.writeSessionCookie(response, guard.issueSession(user.get().getUsername()),
                CommunityAccessGuard.SESSION_TTL, CommunityAuthSupport.isSecure(request));
        return ActionResult.isSuccess();
    }

    /**
     * Ends the session held by this browser.
     * <p>
     * Endpoint: {@code POST /api/community/auth/logout}.
     *
     * @param request  the incoming request, read for its session cookie.
     * @param response the response the cleared cookie is written to.
     * @return action result for the operation.
     */
    @PostMapping("/logout")
    public ActionResult logout(HttpServletRequest request, HttpServletResponse response) {
        guard.revokeSession(CommunityAuthSupport.sessionCookie(request));
        CommunityAuthSupport.writeSessionCookie(response, "", Duration.ZERO, CommunityAuthSupport.isSecure(request));
        return ActionResult.isSuccess();
    }

    /**
     * Changes the signed-in account's own password.
     * <p>
     * Endpoint: {@code POST /api/community/auth/password}.
     *
     * @param changeRequest request payload carrying the current and new passwords.
     * @param request       the incoming request, read for its session cookie.
     * @param response      the response, used to signal refusal.
     * @return action result for the operation.
     */
    @PostMapping("/password")
    public ActionResult changePassword(@RequestBody CommunityPasswordChangeRequest changeRequest,
            HttpServletRequest request, HttpServletResponse response) {
        Optional<CommunityUser> user = guard.resolveSession(CommunityAuthSupport.sessionCookie(request));
        if (user.isEmpty()) {
            return CommunityAuthSupport.failure(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "community.auth.required");
        }
        String current = changeRequest == null ? null : changeRequest.getCurrentPassword();
        String next = changeRequest == null ? null : changeRequest.getNewPassword();

        // Proving the current password matters even with a valid session: it stops
        // an unattended browser from being used to lock the owner out.
        if (guard.authenticate(user.get().getUsername(), current).isEmpty()) {
            return CommunityAuthSupport.failure(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "community.auth.invalidCredentials");
        }
        if (StringUtils.length(StringUtils.trimToEmpty(next)) < CommunityAuthSupport.MIN_PASSWORD_LENGTH) {
            return CommunityAuthSupport.businessFailure("community.auth.passwordTooShort");
        }
        users.setPassword(user.get().getUsername(), next);
        return ActionResult.isSuccess();
    }
}
