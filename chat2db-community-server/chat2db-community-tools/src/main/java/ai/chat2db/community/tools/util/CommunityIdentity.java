package ai.chat2db.community.tools.util;


import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.model.LoginUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;


public final class CommunityIdentity {

    /** Used when no account is signed in: a desktop build, or sign-in switched off. */
    public static final long USER_ID = -1L;

    public static final long ORGANIZATION_ID = -1L;

    public static final String ORGANIZATION_TOKEN = "community-local";

    public static final String DISPLAY_NAME = "Community Local User";

    public static final String ROLE_CODE = "ADMIN";

    public static final String IDENTITY_SOURCE = "community-fixed";

    private CommunityIdentity() {
    }

    /**
     * A stable id for the signed-in account.
     *
     * Everything the application scopes to a person - the AI model
     * configurations most visibly - is keyed by a numeric user id, and Community
     * used to hand every request the same one. That made a model defined by one
     * account visible to all of them, API key included.
     *
     * A username is not a number and the storage is keyed by one, so the two are
     * bridged here: the same name always yields the same id, across restarts and
     * across containers, because it is derived from the name rather than
     * allocated. Lower-cased first, because accounts are matched that way.
     *
     * Always positive, so it can never collide with the no-account id above.
     */
    public static long userId() {
        String account = currentAccount();
        if (StringUtils.isBlank(account)) {
            return USER_ID;
        }
        return accountUserId(account);
    }

    /**
     * The signed-in account, from wherever it can be found on this thread.
     *
     * The thread local is set by the servlet filter and so only exists on the
     * thread serving the request. Work handed to another thread - the assistant
     * calling a tool, an export, the SQL executor - carries the {@link Context}
     * instead, which is captured and restored at each of those boundaries.
     *
     * Reading only the thread local was a bug: on a tool thread it came back
     * empty, the id fell back to the shared one, and looking up the account's AI
     * model configuration under it found nothing - so the assistant could not
     * resolve a model to answer with and reported that it could not read the
     * database.
     *
     * Context first, because it is the one that survives the hand-off; the
     * thread local second, for the moment on the request thread before the
     * Context has been built from it.
     */
    public static String currentAccount() {
        Context context = ContextUtils.queryContext();
        if (context != null && context.getLoginUser() != null
                && StringUtils.isNotBlank(context.getLoginUser().getAccountName())) {
            return context.getLoginUser().getAccountName();
        }
        return CommunityAccountContext.current();
    }

    static long accountUserId(String account) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(account.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (digest[i] & 0xFFL);
            }
            return value & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; the checked exception is a formality.
            throw new IllegalStateException(e);
        }
    }

    public static LoginUser loginUser() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId());
        loginUser.setDisplayName(DISPLAY_NAME);
        // Everything else about this identity is fixed, because Community has no
        // account system of its own to ask. The account name is the exception:
        // the web deployment does have accounts, and this is how the one signing
        // the request reaches the storage layer.
        loginUser.setAccountName(currentAccount());
        loginUser.setAdmin(Boolean.TRUE);
        loginUser.setRoleCodes(List.of(ROLE_CODE));
        loginUser.setVip(Boolean.TRUE);
        loginUser.setOffline(Boolean.TRUE);
        loginUser.setActivated(Boolean.TRUE);
        loginUser.setRegisterType(IDENTITY_SOURCE);
        return loginUser;
    }

    public static Context context() {
        return Context.builder()
                .loginUser(loginUser())
                .organizationId(ORGANIZATION_ID)
                .organizationToken(ORGANIZATION_TOKEN)
                .token(IDENTITY_SOURCE)
                .build();
    }
}
