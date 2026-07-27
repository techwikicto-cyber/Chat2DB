package ai.chat2db.community.web.api.config.web.interceptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsFilterTest {

    @Test
    void communityAllowsOnlyKnownFrontendOriginsOrMissingOrigin() {
        assertTrue(CorsFilter.allowCommunityOrigin(null));
        assertTrue(CorsFilter.allowCommunityOrigin(""));
        assertTrue(CorsFilter.allowCommunityOrigin("http://127.0.0.1:8888"));
        assertTrue(CorsFilter.allowCommunityOrigin("http://localhost:10825"));

        assertFalse(CorsFilter.allowCommunityOrigin("https://example.com"));
        assertFalse(CorsFilter.allowCommunityOrigin("http://127.0.0.1:3000"));
    }

    @Test
    void allowsTheOriginTheRequestWasActuallyAddressedTo() {
        // Serving from any host other than localhost used to 403 every font
        // file: fonts are fetched in CORS mode and so carry an Origin header,
        // which the localhost-only allow-list rejected. Ordinary XHR carries no
        // Origin, so the API kept working and the breakage stayed invisible.
        String served = "http://10.0.0.5:10825";

        assertTrue(CorsFilter.isOriginAllowed("http://10.0.0.5:10825", served));
        assertFalse(CorsFilter.isOriginAllowed("http://10.0.0.5:9999", served));
        assertFalse(CorsFilter.isOriginAllowed("http://evil.example.com", served));
    }

    @Test
    void rejectsACrossOriginRequestEvenWhenServedFromLocalhost() {
        assertFalse(CorsFilter.isOriginAllowed("http://attacker.example", "http://127.0.0.1:10825"));
    }

    @Test
    void takesTheOriginFromTheHostTheBrowserAddressed() {
        // Published as 10826 -> 10825: the browser's Origin carries 10826, while
        // this process only ever sees a connection on 10825. Reading the Host
        // header is the only way to compare like with like - reconstructing the
        // origin from the connection 403s every font on such a deployment.
        assertEquals("http://37.191.94.54:10826",
                CorsFilter.resolveOrigin("http", null, null, "37.191.94.54:10826", "37.191.94.54", 10825));

        // Behind a TLS-terminating proxy the scheme and the authority both change.
        assertEquals("https://db.example.com",
                CorsFilter.resolveOrigin("http", "https", "db.example.com", "10.0.0.5:10825", "10.0.0.5", 10825));

        // Forwarding headers may carry a proxy chain; the client's own comes first.
        assertEquals("https://db.example.com",
                CorsFilter.resolveOrigin("http", "https, http", "db.example.com, inner", "10.0.0.5:10825",
                        "10.0.0.5", 10825));

        // HTTP/1.0 sends no Host: fall back to the connection's own view.
        assertEquals("http://10.0.0.5:10825",
                CorsFilter.resolveOrigin("http", null, null, null, "10.0.0.5", 10825));
    }

    @Test
    void staticAssetsSkipTheOriginCheck() {
        // Fonts are fetched in CORS mode, so a mismatch here drops the whole
        // interface back to system fonts. They carry no privilege either way.
        assertTrue(CorsFilter.isPublicAssetPath("/static/front/static/IRANYekanWeb-Regular.woff", ""));
        assertTrue(CorsFilter.isPublicAssetPath("/favicon.ico", ""));
        assertTrue(CorsFilter.isPublicAssetPath("/app/static/front/umi.js", "/app"));

        assertFalse(CorsFilter.isPublicAssetPath("/api/v3/ai/chat/stream", ""));
        assertFalse(CorsFilter.isPublicAssetPath("/", ""));
        assertFalse(CorsFilter.isPublicAssetPath(null, ""));
    }

    @Test
    void omitsTheDefaultPortWhenBuildingAnOrigin() {
        // A browser sends "https://db.example.com", never "https://db.example.com:443".
        assertEquals("https://db.example.com", CorsFilter.buildOrigin("https", "db.example.com", 443));
        assertEquals("http://db.example.com", CorsFilter.buildOrigin("http", "db.example.com", 80));
        assertEquals("http://db.example.com:10825", CorsFilter.buildOrigin("http", "db.example.com", 10825));
        assertEquals("https://db.example.com:8443", CorsFilter.buildOrigin("https", "db.example.com", 8443));
    }
}
