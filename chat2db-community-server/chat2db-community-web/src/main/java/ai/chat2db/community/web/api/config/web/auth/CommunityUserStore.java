package ai.chat2db.community.web.api.config.web.auth;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import ai.chat2db.community.tools.util.ConfigUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Accounts, held as JSON lines beside the rest of the workspace data.
 *
 * The same shape the application already uses for datasources and consoles: one
 * object per line under {@code <data dir>/storage/user/user.json}, read into
 * memory at startup and rewritten whole on change. There is no database here to
 * put a table in, and introducing one for a handful of accounts would be a much
 * larger commitment than the feature warrants.
 *
 * Usernames are the identity and are matched case-insensitively, so "Ali" and
 * "ali" cannot become two accounts.
 */
@Slf4j
@Component
public class CommunityUserStore {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Keyed by lower-cased username. */
    private final Map<String, CommunityUser> users = new ConcurrentHashMap<>();
    private final Path file;

    public CommunityUserStore() {
        this(Path.of(ConfigUtils.getEnvBasePath(), "storage", "user", "user.json"));
    }

    CommunityUserStore(Path file) {
        this.file = file;
        load();
    }

    /** Accounts in a stable order, so the management screen does not reshuffle. */
    public List<CommunityUser> list() {
        List<CommunityUser> all = new ArrayList<>(users.values());
        all.sort((left, right) -> left.getUsername().compareToIgnoreCase(right.getUsername()));
        return all;
    }

    public Optional<CommunityUser> find(String username) {
        if (StringUtils.isBlank(username)) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(key(username)));
    }

    public boolean isEmpty() {
        return users.isEmpty();
    }

    /** True when this is the last enabled admin, who must not be removed or demoted. */
    public boolean isLastEnabledAdmin(String username) {
        Optional<CommunityUser> candidate = find(username);
        if (candidate.isEmpty() || !candidate.get().isAdmin() || !candidate.get().isEnabled()) {
            return false;
        }
        return users.values().stream().filter(CommunityUser::isEnabled).filter(CommunityUser::isAdmin).count() == 1;
    }

    public synchronized CommunityUser create(String username, String password, CommunityRole role) {
        CommunityUser user = new CommunityUser(
                username.trim(),
                PasswordHasher.hash(password),
                role,
                true,
                LocalDateTime.now().format(TIMESTAMP));
        users.put(key(user.getUsername()), user);
        persist();
        return user;
    }

    public synchronized void setPassword(String username, String password) {
        update(username, user -> user.setPasswordHash(PasswordHasher.hash(password)));
    }

    public synchronized void setEnabled(String username, boolean enabled) {
        update(username, user -> user.setEnabled(enabled));
    }

    public synchronized void setRole(String username, CommunityRole role) {
        update(username, user -> user.setRole(role));
    }

    public synchronized boolean delete(String username) {
        if (users.remove(key(username)) == null) {
            return false;
        }
        persist();
        return true;
    }

    private void update(String username, java.util.function.Consumer<CommunityUser> change) {
        CommunityUser user = users.get(key(username));
        if (user == null) {
            return;
        }
        change.accept(user);
        persist();
    }

    private static String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                CommunityUser user = MAPPER.readValue(line.trim(), CommunityUser.class);
                if (user != null && StringUtils.isNotBlank(user.getUsername())) {
                    users.put(key(user.getUsername()), user);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Refusing to start over an unreadable account file would strand the
            // operator with no way in. Carry on empty; the bootstrap below then
            // recreates an admin and says so in the log.
            log.error("[chat2db] Could not read {}. Starting with no accounts.", file, e);
        }
    }

    /**
     * Rewrites the whole file through a temporary one, so an interrupted write
     * cannot leave a half-written account list behind.
     */
    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), ".user-", ".tmp");
            try {
                StringBuilder content = new StringBuilder();
                for (CommunityUser user : list()) {
                    content.append(MAPPER.writeValueAsString(user)).append(System.lineSeparator());
                }
                Files.writeString(tmp, content.toString(), StandardCharsets.UTF_8);
                restrictToOwner(tmp.toFile());
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
            restrictToOwner(file.toFile());
        } catch (IOException e) {
            log.error("[chat2db] Could not write {}", file, e);
        }
    }

    /** The file holds password hashes; keep it away from other accounts on the host. */
    private static void restrictToOwner(File target) {
        if (!target.setReadable(false, false) || !target.setWritable(false, false)) {
            log.debug("[chat2db] Could not tighten permissions on {}", target);
        }
        target.setReadable(true, true);
        target.setWritable(true, true);
    }
}
