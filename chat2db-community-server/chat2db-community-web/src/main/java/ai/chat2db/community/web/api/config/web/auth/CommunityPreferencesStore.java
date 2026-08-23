package ai.chat2db.community.web.api.config.web.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ai.chat2db.community.tools.util.ConfigUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * What each account chose in Settings, kept on the server rather than in the
 * browser.
 *
 * Theme, language, fonts, editor and grid options and the selected AI model
 * used to live in localStorage only, so signing in from a second browser - or
 * the same one after clearing site data - started from defaults, and the
 * account you signed in as made no difference to any of it. Storing them here
 * makes them follow the person instead of the machine.
 *
 * The contents are opaque: whatever the interface sends is what comes back. The
 * server has no opinion on which settings exist, so adding one later needs no
 * change here.
 *
 * One file for every account, keyed by lower-cased username, in the same shape
 * and directory as the account list itself - and left there by the workspace
 * migration for the same reason the account list is.
 */
@Slf4j
@Component
public class CommunityPreferencesStore {

    /**
     * A ceiling on one account's preferences. They are a few hundred bytes of
     * settings; anything approaching this is a mistake or an attempt to use the
     * server as storage, and either way the disk is shared with the data.
     */
    static final int MAX_BYTES = 128 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Map<String, Object>>> SHAPE = new TypeReference<>() {
    };

    /** Keyed by lower-cased username, matching how accounts are matched. */
    private final Map<String, Map<String, Object>> preferences = new ConcurrentHashMap<>();
    private final Path file;

    public CommunityPreferencesStore() {
        this(Path.of(ConfigUtils.getEnvBasePath(), "storage", "user", "preferences.json"));
    }

    CommunityPreferencesStore(Path file) {
        this.file = file;
        load();
    }

    /** What this account has saved, or an empty map if it has saved nothing. */
    public Map<String, Object> find(String username) {
        if (StringUtils.isBlank(username)) {
            return Map.of();
        }
        return Map.copyOf(preferences.getOrDefault(key(username), Map.of()));
    }

    public synchronized void save(String username, Map<String, Object> value) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        preferences.put(key(username), value == null ? Map.of() : new HashMap<>(value));
        persist();
    }

    /** Used when an account is deleted, so a later account of that name starts clean. */
    public synchronized void delete(String username) {
        if (StringUtils.isBlank(username)) {
            return;
        }
        if (preferences.remove(key(username)) != null) {
            persist();
        }
    }

    /** Whether a payload is small enough to keep. */
    public static boolean isWithinLimit(Map<String, Object> value) {
        try {
            return MAPPER.writeValueAsBytes(value == null ? Map.of() : value).length <= MAX_BYTES;
        } catch (IOException e) {
            return false;
        }
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (StringUtils.isBlank(content)) {
                return;
            }
            preferences.putAll(MAPPER.readValue(content, SHAPE));
        } catch (IOException | RuntimeException e) {
            // Settings are a convenience. A file this cannot read must not stop
            // anyone signing in - it is rewritten on the next save.
            log.warn("[chat2db] Could not read {}; every account starts from default settings.", file, e);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            // Written beside and moved into place, so a crash mid-write cannot
            // leave a half-file that loses everyone's settings at once.
            Path temporary = Files.createTempFile(file.getParent(), ".preferences-", ".tmp");
            Files.writeString(temporary, MAPPER.writeValueAsString(preferences), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[chat2db] Could not write {}. Settings changed now will not survive a restart.", file, e);
        }
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
