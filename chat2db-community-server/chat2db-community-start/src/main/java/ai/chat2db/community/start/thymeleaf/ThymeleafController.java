package ai.chat2db.community.start.thymeleaf;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page application entry points, including legacy browser routes.
 */
@Controller
@Order(Integer.MIN_VALUE)
public class ThymeleafController {

    /**
     * Returns the main single-page application entry for mapped browser routes.
     * <p>
     * Endpoint: {@code GET multiple mapped routes}.
     *
     * @param response the response whose caching is being suppressed.
     * @return string value for the request.
     */
    @GetMapping({
            "/",
            "/connections",
            "/dashboard",
            "/team",
            "/workspace",
            "/permission",
            "/chat",
            "/chat.html",
            "/approval",
            "/organization",
            "/reset_password",
            "/web/",
            "/web/**",
            "/login/**",
            "/ai/**",
            "/model/**",
            "/pay",
            "/invite",
            "/price",
            "/settings/**",
            "/chat/share/**",
            "/dashboard/share/**",
            "/knowledge-management"
    })
    public String index(HttpServletResponse response) {
        // This page names the hashed bundles a build produced, and nothing in
        // its own URL changes between deployments. Left to its own devices a
        // browser may reuse it from heuristic caching and go on asking for the
        // previous build's files, so say plainly that it must not be kept.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate");
        return "index";
    }
}
