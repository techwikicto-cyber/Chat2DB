package ai.chat2db.community.web.api.config.web.auth;

import java.time.Duration;

import ai.chat2db.community.tools.wrapper.result.ActionResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Shared between the sign-in and account-management controllers. */
public final class CommunityAuthSupport {

    /**
     * Short enough to accept an existing habit, long enough to be worth typing.
     * The gate this protects is reachable from the network, so trivially short
     * passwords are refused outright rather than merely discouraged.
     */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private CommunityAuthSupport() {
    }

    /**
     * Letters, digits, dot, dash and underscore, 2 to 32 characters.
     *
     * Narrow on purpose: a username lands in a URL path and in log lines, and
     * neither wants surprises.
     */
    public static boolean isValidUsername(String username) {
        if (username == null || username.length() < 2 || username.length() > 32) {
            return false;
        }
        return username.toLowerCase(java.util.Locale.ROOT).matches("[a-z0-9._-]+");
    }

    /** Whether a proposed password clears the minimum length. */
    public static boolean isTooShort(String password) {
        return password == null || password.trim().length() < MIN_PASSWORD_LENGTH;
    }

    public static String sessionCookie(HttpServletRequest request) {
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
     * A refusal the caller is expected to read and act on: a password too short,
     * a username already taken, the last admin.
     *
     * Deliberately HTTP 200 with {@code success: false}, like every other
     * business failure in this application. The browser client parses the
     * envelope only on a 2xx; a 4xx is turned into a transport error before the
     * body is looked at, so the reason never reaches the screen and the user is
     * told "failed" with no explanation.
     */
    public static ActionResult businessFailure(String errorCode) {
        ActionResult result = ActionResult.isSuccess();
        result.success(false);
        result.errorCode(errorCode);
        return result;
    }

    /**
     * A protocol-level refusal: not signed in, or not permitted.
     *
     * These keep their status, because the status is the message - the filter
     * answers the same way for the same conditions, and clients act on the code
     * rather than the body.
     */
    public static ActionResult failure(HttpServletResponse response, int status, String errorCode) {
        response.setStatus(status);
        return businessFailure(errorCode);
    }

    /**
     * HttpOnly so script cannot read the token, SameSite=Lax so another site
     * cannot ride the session, and Secure only over HTTPS - setting it on a plain
     * HTTP deployment would make the cookie silently unusable.
     */
    public static void writeSessionCookie(HttpServletResponse response, String value, Duration maxAge,
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
    public static boolean isSecure(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto.split(",")[0].trim());
        }
        return request.isSecure();
    }
}
