package ai.chat2db.community.web.api.config.web.interceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import ai.chat2db.community.tools.util.ConfigUtils;
import org.apache.commons.lang3.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;


@Component
public class CorsFilter implements Filter {

    private static final Set<String> COMMUNITY_ALLOWED_ORIGINS = Set.of(
            "http://127.0.0.1:8888",
            "http://localhost:8888",
            "http://127.0.0.1:10825",
            "http://localhost:10825"
    );

    /**
     * Extra origins to trust, as a comma-separated list. Needed when the browser
     * reaches the app through a reverse proxy, so the origin it sends is the
     * public name rather than the address the request arrives on.
     */
    private static final String ALLOWED_ORIGINS_PROPERTY = "chat2db.community.allowed-origins";
    private static final String ALLOWED_ORIGINS_ENV = "CHAT2DB_COMMUNITY_ALLOWED_ORIGINS";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
        throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse)res;
        HttpServletRequest request = (HttpServletRequest)req;
        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (ConfigUtils.isCommunity() && !isPublicAsset(request) && !allowCommunityOrigin(origin, request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        setCorsHeaders(response, origin);
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, DBHUB, uid, Time-Zone");
        chain.doFilter(req, res);
    }

    /**
     * The bundled front end: scripts, styles, fonts and images the server just
     * shipped with the page.
     *
     * The origin check exists to keep another site from driving this API from a
     * user's browser. Static files carry no privilege and no side effect, so
     * refusing them buys nothing and costs a lot: browsers fetch fonts in CORS
     * mode, so a mismatch here silently drops the interface back to system fonts
     * with only a console 403 to show for it - which is what happens whenever the
     * published port differs from the one the container listens on, since the
     * request's own origin is then reconstructed from the wrong port.
     */
    static boolean isPublicAsset(HttpServletRequest request) {
        return isPublicAssetPath(request.getRequestURI(), request.getContextPath());
    }

    /** Same as above, taking the raw path so it can be unit tested. */
    static boolean isPublicAssetPath(String requestUri, String contextPath) {
        if (requestUri == null) {
            return false;
        }
        String path = requestUri;
        if (StringUtils.isNotEmpty(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith("/static/") || path.equals("/favicon.ico");
    }

    static boolean allowCommunityOrigin(String origin) {
        return isOriginAllowed(origin, null);
    }

    /**
     * The allow-list alone only ever matched localhost, which broke every
     * deployment reached by any other name. Browsers omit Origin on ordinary
     * navigations and XHR, so the API kept working and the breakage stayed
     * invisible - but font files are fetched in CORS mode even same-origin, so
     * every woff2 came back 403 and the UI silently fell back to system fonts.
     *
     * Same-origin requests are therefore allowed on whatever host the server is
     * actually serving, which is not a relaxation: an origin equal to this
     * request's own is by definition the page the server just served.
     */
    static boolean allowCommunityOrigin(String origin, HttpServletRequest request) {
        return isOriginAllowed(origin, request == null ? null : requestOrigin(request));
    }

    /** Same as above, taking the request's own origin directly so it can be unit tested. */
    static boolean isOriginAllowed(String origin, String requestOrigin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        if (COMMUNITY_ALLOWED_ORIGINS.contains(origin)) {
            return true;
        }
        if (requestOrigin != null && origin.equals(requestOrigin)) {
            return true;
        }
        return configuredOrigins().contains(origin);
    }

    /**
     * The origin this request was addressed to, e.g. "http://10.0.0.5:10825".
     *
     * Taken from the Host header, because that is the authority the browser used
     * to compose Origin in the first place - published port included, which is
     * not necessarily the port this process listens on. A container published as
     * 10826 -> 10825, or anything behind a reverse proxy, sends a Host the
     * servlet container's own view of the connection cannot reproduce.
     *
     * A cross-site page cannot influence either header: the browser derives Host
     * from the URL and refuses to let script set it. Whoever can forge these can
     * equally well send no Origin at all, which is already allowed.
     */
    static String requestOrigin(HttpServletRequest request) {
        return resolveOrigin(
                request.getScheme(),
                request.getHeader("X-Forwarded-Proto"),
                request.getHeader("X-Forwarded-Host"),
                request.getHeader(HttpHeaders.HOST),
                request.getServerName(),
                request.getServerPort());
    }

    /** Same as above, taking the raw values so it can be unit tested. */
    static String resolveOrigin(String connectionScheme, String forwardedProto, String forwardedHost,
            String hostHeader, String serverName, int serverPort) {
        String scheme = firstValue(forwardedProto);
        if (scheme == null) {
            scheme = connectionScheme;
        }
        if (scheme == null) {
            return null;
        }
        String authority = firstValue(forwardedHost);
        if (authority == null) {
            authority = firstValue(hostHeader);
        }
        if (authority != null) {
            return scheme + "://" + authority;
        }
        // No Host header at all: HTTP/1.0. Fall back to the connection's own view.
        return serverName == null ? null : buildOrigin(scheme, serverName, serverPort);
    }

    /** First entry of a possibly comma-separated forwarding header, or null when absent. */
    static String firstValue(String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return null;
        }
        String first = headerValue.split(",")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /** A browser omits the port when it is the scheme's default, so this must too. */
    static String buildOrigin(String scheme, String host, int port) {
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private static Set<String> configuredOrigins() {
        String raw = System.getProperty(ALLOWED_ORIGINS_PROPERTY);
        if (StringUtils.isBlank(raw)) {
            raw = System.getenv(ALLOWED_ORIGINS_ENV);
        }
        if (StringUtils.isBlank(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void setCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }

}
