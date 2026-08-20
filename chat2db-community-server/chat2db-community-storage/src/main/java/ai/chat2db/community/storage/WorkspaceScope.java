package ai.chat2db.community.storage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.util.ConfigUtils;
import ai.chat2db.community.tools.util.ContextUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Which workspace on disk the request being served belongs to.
 *
 * Every account gets its own directory under {@code storage/users}, holding the
 * same tree the application has always written - datasources, consoles, history,
 * dashboards and the rest. Isolation is therefore structural: there is no filter
 * anywhere deciding which rows a person may see, because the rows another person
 * owns are in a file this request never opens. A read path added later is scoped
 * whether or not its author thought about accounts.
 *
 * Without an account - the desktop build, a deployment with sign-in switched
 * off, or the MCP endpoint - the shared workspace directly under {@code storage}
 * is used, exactly as before.
 */
public final class WorkspaceScope {

    /** Parent of the per-account workspaces. */
    public static final String USERS_DIR = "users";

    private WorkspaceScope() {
    }

    /** The shared workspace, used when no account is signed in. */
    public static String sharedBasePath() {
        return ConfigUtils.getEnvBasePath() + File.separator + "storage";
    }

    public static String usersBasePath() {
        return sharedBasePath() + File.separator + USERS_DIR;
    }

    /** Where a named account's workspace lives. */
    public static String basePathFor(String account) {
        if (StringUtils.isBlank(account)) {
            return sharedBasePath();
        }
        return usersBasePath() + File.separator + directoryName(account);
    }

    /** Where the request currently being served should read and write. */
    public static String currentBasePath() {
        return basePathFor(currentAccount());
    }

    /** The signed-in account, or null. Read from the request context, which async work carries along. */
    public static String currentAccount() {
        Context context = ContextUtils.queryContext();
        if (context == null || context.getLoginUser() == null) {
            return null;
        }
        return StringUtils.trimToNull(context.getLoginUser().getAccountName());
    }

    /**
     * A directory name for an account.
     *
     * Usernames are free text and directories are not, so the readable part is
     * reduced to characters that are safe everywhere - which on its own would
     * let "a b" and "a/b" collide, and would let a crafted name climb out of the
     * workspace entirely. The hash suffix settles both: it is taken from the
     * whole original name, so two different accounts cannot land in one
     * directory however they are spelled, and the readable part is then only
     * there to make the directory recognisable.
     *
     * Lower-cased first, because the account store already treats "Ali" and
     * "ali" as one account and the directory must agree with it.
     */
    static String directoryName(String account) {
        String normalised = account.trim().toLowerCase(Locale.ROOT);
        StringBuilder readable = new StringBuilder(normalised.length());
        for (char character : normalised.toCharArray()) {
            boolean safe = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '_';
            readable.append(safe ? character : '_');
        }
        String trimmed = StringUtils.left(readable.toString(), 32);
        return trimmed + "-" + shortHash(normalised);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; the checked exception is a formality.
            throw new IllegalStateException(e);
        }
    }
}
