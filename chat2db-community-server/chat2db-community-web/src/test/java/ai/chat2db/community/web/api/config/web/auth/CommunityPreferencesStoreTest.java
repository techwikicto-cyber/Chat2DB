package ai.chat2db.community.web.api.config.web.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityPreferencesStoreTest {

    @TempDir
    Path dataDir;

    private Path file() {
        return dataDir.resolve("preferences.json");
    }

    @Test
    void whatOneAccountSavesIsWhatItReadsBack() {
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        store.save("foad", Map.of("theme", "dark", "language", "fa-IR"));

        assertEquals(Map.of("theme", "dark", "language", "fa-IR"), store.find("foad"));
    }

    @Test
    void oneAccountsSettingsAreNotAnothers() {
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        store.save("foad", Map.of("theme", "dark"));

        assertTrue(store.find("nima").isEmpty(), "settings leaked between accounts");
        assertTrue(store.find("nobody").isEmpty());
    }

    @Test
    void theSameAccountIsRecognisedHoweverItIsSpelled() {
        // The account store matches usernames case-insensitively; signing in as
        // "Foad" must not open a second, empty set of settings.
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        store.save("Foad", Map.of("theme", "dark"));

        assertEquals(Map.of("theme", "dark"), store.find("foad"));
        assertEquals(Map.of("theme", "dark"), store.find("  FOAD "));
    }

    @Test
    void settingsSurviveARestart() {
        new CommunityPreferencesStore(file()).save("foad", Map.of("theme", "dark"));

        assertEquals(Map.of("theme", "dark"), new CommunityPreferencesStore(file()).find("foad"));
    }

    @Test
    void savingReplacesRatherThanMerges() {
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        store.save("foad", Map.of("theme", "dark", "language", "fa-IR"));
        store.save("foad", Map.of("theme", "light"));

        assertEquals(Map.of("theme", "light"), store.find("foad"));
    }

    @Test
    void deletingAnAccountTakesItsSettingsWithIt() {
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        store.save("foad", Map.of("theme", "dark"));
        store.save("nima", Map.of("theme", "light"));

        store.delete("foad");

        // Otherwise a later account taking the same name would inherit them.
        assertTrue(store.find("foad").isEmpty());
        assertEquals(Map.of("theme", "light"), store.find("nima"));
    }

    @Test
    void anUnreadableFileCostsSettingsRatherThanSignIn() throws IOException {
        Files.writeString(file(), "{ this is not json", StandardCharsets.UTF_8);

        CommunityPreferencesStore store = new CommunityPreferencesStore(file());

        assertTrue(store.find("foad").isEmpty());
        // And it recovers: the next save rewrites the file.
        store.save("foad", Map.of("theme", "dark"));
        assertEquals(Map.of("theme", "dark"), new CommunityPreferencesStore(file()).find("foad"));
    }

    @Test
    void aPayloadTooLargeToBeSettingsIsRefused() {
        assertTrue(CommunityPreferencesStore.isWithinLimit(Map.of("theme", "dark")));
        assertTrue(CommunityPreferencesStore.isWithinLimit(null));

        Map<String, Object> oversized = new HashMap<>();
        oversized.put("blob", "x".repeat(CommunityPreferencesStore.MAX_BYTES + 1));
        assertFalse(CommunityPreferencesStore.isWithinLimit(oversized));
    }

    @Test
    void aSettingWithNoValueSurvivesTheRoundTrip() {
        // "no model chosen" is a null, and Map.copyOf rejects null values - so
        // this saved happily and then threw on the way back out, which read as
        // settings that would not load at all.
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("selectedModel", null);
        withNull.put("theme", "dark");

        store.save("foad", withNull);

        assertEquals("dark", store.find("foad").get("theme"));
        assertTrue(store.find("foad").containsKey("selectedModel"));
        assertEquals("dark", new CommunityPreferencesStore(file()).find("foad").get("theme"));
    }

    @Test
    void whatComesBackCannotBeChangedFromOutside() {
        CommunityPreferencesStore store = new CommunityPreferencesStore(file());
        Map<String, Object> saved = new HashMap<>();
        saved.put("theme", "dark");
        store.save("foad", saved);

        // Mutating the caller's map must not reach into the store.
        saved.put("theme", "light");

        assertEquals("dark", store.find("foad").get("theme"));
    }
}
