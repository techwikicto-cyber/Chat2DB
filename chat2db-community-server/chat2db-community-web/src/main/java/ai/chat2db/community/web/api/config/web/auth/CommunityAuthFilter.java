package ai.chat2db.community.web.api.config.web.auth;

import java.io.IOException;

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
 * Requires a signed-in session for the API when a shared password is configured.
 *
 * Enforced here rather than in the interface, because hiding a screen is not
 * access control: every endpoint this guards is reachable directly.
 */
@Component
public class CommunityAuthFilter extends OncePerRequestFilter {

    /** Reachable signed out, or there would be no way to sign in. */
    static final String AUTH_PATH_PREFIX = "/api/community/auth/";

    private final CommunityAccessGuard guard;

    public CommunityAuthFilter(CommunityAccessGuard guard) {
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!requiresSession(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (guard.isValidSession(sessionToken(request))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"errorCode\":\"community.auth.required\"}");
    }

    private boolean requiresSession(HttpServletRequest request) {
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

    /** Same as above, taking the raw path so it can be unit tested. */
    static boolean isGuardedPath(String requestUri, String contextPath) {
        if (requestUri == null) {
            return false;
        }
        String path = requestUri;
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith("/api/") && !path.startsWith(AUTH_PATH_PREFIX);
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
