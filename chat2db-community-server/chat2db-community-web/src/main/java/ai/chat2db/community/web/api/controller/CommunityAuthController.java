package ai.chat2db.community.web.api.controller;

import java.time.Duration;

import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.web.api.config.web.auth.CommunityAccessGuard;
import ai.chat2db.community.web.api.model.request.auth.CommunityLoginRequest;
import ai.chat2db.community.web.api.model.response.auth.CommunityAuthStatusResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in for the Community shared-password gate.
 *
 * Reachable without a session - see {@code CommunityAuthFilter} - because it is
 * how a session is obtained in the first place.
 */
@RestController
@RequestMapping("/api/community/auth")
public class CommunityAuthController {

    private final CommunityAccessGuard guard;

    public CommunityAuthController(CommunityAccessGuard guard) {
        this.guard = guard;
    }

    /**
     * Reports whether a password is configured and whether this browser holds a
     * valid session.
     * <p>
     * Endpoint: {@code GET /api/community/auth/status}.
     *
     * @param request the incoming request, read for its session cookie.
     * @return data result describing what the interface should show.
     */
    @GetMapping("/status")
    public DataResult<CommunityAuthStatusResponse> status(HttpServletRequest request) {
        boolean required = guard.isEnabled();
        boolean authenticated = !required || guard.isValidSession(readSessionCookie(request));
        return DataResult.of(new CommunityAuthStatusResponse(required, authenticated));
    }

    /**
     * Exchanges the shared password for a session cookie.
     * <p>
     * Endpoint: {@code POST /api/community/auth/login}.
     *
     * @param loginRequest request payload carrying the password.
     * @param request      the incoming request, read to decide cookie flags.
     * @param response     the response the session cookie is written to.
     * @return action result; unsuccessful with HTTP 401 when the password is wrong.
     */
    @PostMapping("/login")
    public ActionResult login(@RequestBody CommunityLoginRequest loginRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (!guard.isEnabled()) {
            // Nothing to sign in to. Say so rather than minting a useless cookie.
            return ActionResult.isSuccess();
        }
        if (!guard.matches(loginRequest == null ? null : loginRequest.getPassword())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ActionResult failure = ActionResult.isSuccess();
            failure.success(false);
            failure.errorCode("community.auth.invalidPassword");
            return failure;
        }
        writeSessionCookie(response, guard.issueSession(), CommunityAccessGuard.SESSION_TTL, isSecure(request));
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
        guard.revokeSession(readSessionCookie(request));
        writeSessionCookie(response, "", Duration.ZERO, isSecure(request));
        return ActionResult.isSuccess();
    }

    private static String readSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (CommunityAccessGuard.SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * HttpOnly so script cannot read the token, SameSite=Lax so another site
     * cannot ride the session, and Secure only over HTTPS - setting it on a plain
     * HTTP deployment would make the cookie silently unusable.
     */
    private static void writeSessionCookie(HttpServletResponse response, String value, Duration maxAge,
            boolean secure) {
        StringBuilder cookie = new StringBuilder()
                .append(CommunityAccessGuard.SESSION_COOKIE).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAge.toSeconds());
        if (secure) {
            cookie.append("; Secure");
        }
        response.addHeader("Set-Cookie", cookie.toString());
    }

    /** True when the browser reached us over HTTPS, directly or through a proxy. */
    private static boolean isSecure(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.split(",")[0].trim());
        }
        return request.isSecure();
    }
}
