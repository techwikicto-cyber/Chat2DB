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

        if (ConfigUtils.isCommunity() && !allowCommunityOrigin(origin, request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        setCorsHeaders(response, origin);
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, OPTIONS, DELETE");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, DBHUB, uid, Time-Zone");
        chain.doFilter(req, res);
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

    /** The origin this request was addressed to, e.g. "http://10.0.0.5:10825". */
    static String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        if (scheme == null || host == null) {
            return null;
        }
        return buildOrigin(scheme, host, request.getServerPort());
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
