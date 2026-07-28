package ai.chat2db.community.tools.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link LogUtils#maskString(String)}, which decides what a request
 * or response body looks like once it reaches the log.
 *
 * <p>Two things have to hold at once, and the previous implementation - overwrite
 * every fourth character - managed neither: a credential must not be readable,
 * and everything that is not a credential must be, because a log nobody can read
 * is not worth writing.
 */
class LogUtilsMaskStringTest {

    @Test
    void hidesPasswordAndLeavesTheRestAlone() {
        String masked = LogUtils.maskString(
            "{\"url\":\"jdbc:mysql://db:3306/\",\"user\":\"hokm_user\",\"password\":\"s3cr3t-value\"}");

        assertEquals("{\"url\":\"jdbc:mysql://db:3306/\",\"user\":\"hokm_user\",\"password\":\"***\"}", masked);
    }

    @Test
    void hidesCredentialsWhateverTheyArePrefixedWith() {
        String masked = LogUtils.maskString(
            "{\"encryptedPassword\":\"abc\",\"openAiApiKey\":\"sk-123\",\"accessToken\":\"t\",\"sshPassphrase\":\"p\"}");

        assertFalse(masked.contains("abc"));
        assertFalse(masked.contains("sk-123"));
        assertFalse(masked.contains("\"t\""));
        assertFalse(masked.contains("\"p\""));
    }

    @Test
    void keepsFieldNamesThatOnlySoundSensitive() {
        String body = "{\"authenticationType\":\"password\",\"tokenType\":\"bearer\"}";

        assertEquals(body, LogUtils.maskString(body));
    }

    @Test
    void keepsTheReadablePartOfTheBodyIntact() {
        String masked = LogUtils.maskString(
            "{\"sql\":\"SELECT count(*) FROM public_chat\",\"password\":\"hunter2\",\"success\":true}");

        assertTrue(masked.contains("SELECT count(*) FROM public_chat"), "the statement should still be readable");
        assertTrue(masked.contains("\"success\":true"), "unrelated fields should be untouched");
        assertFalse(masked.contains("hunter2"));
        assertFalse(masked.contains("*\"s"), "no character-by-character smudging");
    }

    @Test
    void leavesAnEmptyOrNullCredentialAsItIs() {
        String body = "{\"password\":\"\",\"token\":null}";

        assertEquals(body, LogUtils.maskString(body));
    }

    @Test
    void hidesCredentialsInQueryStringsToo() {
        assertEquals("id=17&password=***&pageSize=40", LogUtils.maskString("id=17&password=abc123&pageSize=40"));
    }

    @Test
    void survivesValuesCarryingQuotesAndBraces() {
        String masked = LogUtils.maskString("{\"password\":\"a\\\"b}c\",\"name\":\"keep\"}");

        assertFalse(masked.contains("a\\\"b}c"));
        assertTrue(masked.contains("\"name\":\"keep\""));
    }

    @Test
    void passesBlankInputStraightThrough() {
        assertEquals("", LogUtils.maskString(""));
        assertEquals(null, LogUtils.maskString(null));
    }
}
