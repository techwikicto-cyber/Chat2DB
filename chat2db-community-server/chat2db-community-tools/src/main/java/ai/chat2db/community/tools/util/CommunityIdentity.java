package ai.chat2db.community.tools.util;


import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.model.LoginUser;
import java.util.List;


public final class CommunityIdentity {

    public static final long USER_ID = -1L;

    public static final long ORGANIZATION_ID = -1L;

    public static final String ORGANIZATION_TOKEN = "community-local";

    public static final String DISPLAY_NAME = "Community Local User";

    public static final String ROLE_CODE = "ADMIN";

    public static final String IDENTITY_SOURCE = "community-fixed";

    private CommunityIdentity() {
    }

    public static LoginUser loginUser() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setDisplayName(DISPLAY_NAME);
        // Everything else about this identity is fixed, because Community has no
        // account system of its own to ask. The account name is the exception:
        // the web deployment does have accounts, and this is how the one signing
        // the request reaches the storage layer.
        loginUser.setAccountName(CommunityAccountContext.current());
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
