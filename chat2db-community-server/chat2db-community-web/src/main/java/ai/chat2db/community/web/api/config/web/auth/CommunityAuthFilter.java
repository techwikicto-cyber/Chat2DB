package ai.chat2db.community.web.api.config.web.auth;

import java.io.IOException;
import java.util.Optional;

import ai.chat2db.community.tools.util.ConfigUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a session for the API, and the admin role for account management.
 *
 * Enforced here rather than in the interface, because hiding a screen is not
 * access control: every endpoint this guards is reachable directly.
 */
@Component
public class CommunityAuthFilter extends OncePerRequestFilter {

    /** Reachable signed out, or there would be no way to sign in. */
    static final String AUTH_PATH_PREFIX = "/api/community/auth/";

    /** Account management, reachable only by an admin. */
    static final String ADMIN_PATH_PREFIX = "/api/community/users";

    /** Set on the request so controllers can see who is calling. */
    public static final String CURRENT_USER_ATTRIBUTE = "chat2db.community.currentUser";

    private final CommunityAccessGuard guard;

    public CommunityAuthFilter(CommunityAccessGuard guard) {
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CommunityUser> user = guard.resolveSession(sessionToken(request));
        user.ifPresent(value -> request.setAttribute(CURRENT_USER_ATTRIBUTE, value));

        if (!isGuarded(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (user.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "community.auth.required");
            return;
        }
        if (isAdminPath(request.getRequestURI(), request.getContextPath()) && !user.get().isAdmin()) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "community.auth.adminRequired");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isGuarded(HttpServletRequest request) {
        if (!ConfigUtils.isCommunity() || !guard.isEnabled()) {
            return false;
        }
        // A preflight carries no cookies by definition, so rejecting it would
        // fail the request before the real one is ever sent.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        return isGuardedPath(request.getRequestURI(), request.getContextPath());
    }

    private static void reject(HttpServletResponse response, int status, String errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"errorCode\":\"" + errorCode + "\"}");
    }

    /** Same as above, taking the raw path so it can be unit tested. */
    static boolean isGuardedPath(String requestUri, String contextPath) {
        String path = stripContext(requestUri, contextPath);
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/") && !path.startsWith(AUTH_PATH_PREFIX);
    }

    static boolean isAdminPath(String requestUri, String contextPath) {
        String path = stripContext(requestUri, contextPath);
        return path != null && path.startsWith(ADMIN_PATH_PREFIX);
    }

    private static String stripContext(String requestUri, String contextPath) {
        if (requestUri == null) {
            return null;
        }
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    static String sessionToken(HttpServletRequest request) {
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
}
