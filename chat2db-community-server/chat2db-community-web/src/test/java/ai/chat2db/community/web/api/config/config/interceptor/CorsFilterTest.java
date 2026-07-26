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
    void omitsTheDefaultPortWhenBuildingAnOrigin() {
        // A browser sends "https://db.example.com", never "https://db.example.com:443".
        assertEquals("https://db.example.com", CorsFilter.buildOrigin("https", "db.example.com", 443));
        assertEquals("http://db.example.com", CorsFilter.buildOrigin("http", "db.example.com", 80));
        assertEquals("http://db.example.com:10825", CorsFilter.buildOrigin("http", "db.example.com", 10825));
        assertEquals("https://db.example.com:8443", CorsFilter.buildOrigin("https", "db.example.com", 8443));
    }
}
