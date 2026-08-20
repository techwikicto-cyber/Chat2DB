package ai.chat2db.community.tools.util;

import org.apache.commons.lang3.StringUtils;

/**
 * The account behind the request currently being served, for the short window
 * between the auth filter recognising a session and the controller aspect
 * building the {@link ai.chat2db.community.tools.model.Context} from it.
 *
 * A thread local rather than a parameter because the identity has to reach the
 * storage layer, which sits several modules below the servlet and takes no
 * request. That is the shape this application already uses for the connection
 * and the request context, so async work already knows to carry it: everything
 * that hands work to another thread captures the Context and restores it there,
 * and the account rides along inside it.
 *
 * Empty means no account - a desktop build, a deployment with sign-in switched
 * off, or a request that arrived without a session. Storage falls back to the
 * shared workspace in that case rather than guessing at an owner.
 */
public final class CommunityAccountContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CommunityAccountContext() {
    }

    public static void set(String account) {
        if (StringUtils.isBlank(account)) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(account.trim());
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** The signed-in account, or null when there is none. */
    public static String current() {
        return CURRENT.get();
    }
}
