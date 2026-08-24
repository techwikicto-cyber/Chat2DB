package ai.chat2db.community.domain.core.impl.ai;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.chat2db.community.domain.api.model.request.ai.AiListTablesRequest;
import ai.chat2db.community.domain.api.model.request.ai.AiToolContextRequest;
import ai.chat2db.community.domain.api.model.runtime.ConnectionProfile;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.domain.api.service.db.IDbTableService;
import ai.chat2db.community.tools.model.Context;
import ai.chat2db.community.tools.model.LoginUser;
import ai.chat2db.community.tools.util.ContextUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Whose workspace a tool reads.
 *
 * <p>A tool runs on whichever thread the streaming client is on, never the one
 * that served the request, so it has no identity of its own and has to be
 * handed the one captured from the request. Storage is per account, so a tool
 * that steps outside that Context - even for one call - reads a workspace that
 * is not the asker's and reports their datasource missing.
 *
 * <p>Which is what happened: the datasource was resolved inside the Context and
 * then <em>bound</em> outside it, and binding looks it up a second time. Listing
 * datasources worked, because it has no second lookup; everything the assistant
 * needed to actually read a schema came back "datasource.not.found".
 */
class AiToolServiceImplRequestContextTest {

    private static final String ACCOUNT = "foad";
    private static final long DATA_SOURCE_ID = 1787475782532999L;

    /** Every account seen by the connection service, in call order. */
    private final List<String> accountsSeen = Collections.synchronizedList(new ArrayList<>());

    @Test
    void aToolReadsTheWorkspaceOfWhoeverAsked() throws Exception {
        AiToolServiceImpl service = service();
        AiListTablesRequest request = listTablesRequest(contextFor(ACCOUNT));

        // No Context here, exactly like the thread a tool call lands on.
        ExecutorService toolThread = Executors.newSingleThreadExecutor();
        try {
            toolThread.submit(() -> service.listAllTables(request)).get();
        } finally {
            toolThread.shutdownNow();
        }

        // buildProfile and bindProfile both resolve the datasource. Both have to
        // do it as the account that asked.
        assertEquals(List.of(ACCOUNT, ACCOUNT), accountsSeen,
                "a datasource lookup ran outside the asking account's Context");
    }

    @Test
    void theContextIsPutBackWhenTheToolIsDone() throws Exception {
        AiToolServiceImpl service = service();
        AiListTablesRequest request = listTablesRequest(contextFor(ACCOUNT));

        ExecutorService toolThread = Executors.newSingleThreadExecutor();
        try {
            toolThread.submit(() -> service.listAllTables(request)).get();
            // A pooled thread outlives the call; leaving the Context on it would
            // hand the next question this account's workspace.
            assertEquals(null, toolThread.submit(ContextUtils::queryContext).get());
        } finally {
            toolThread.shutdownNow();
        }
    }

    private AiToolServiceImpl service() throws Exception {
        AiToolServiceImpl service = new AiToolServiceImpl();
        inject(service, "connectionContextService", connectionContextService());
        inject(service, "tableService", noTables());
        return service;
    }

    /**
     * Stands in for the real connection service, recording who each datasource
     * lookup was made as. Both calls the tool makes - building the profile and
     * binding it - go through here.
     */
    private IDbConnectionContextService connectionContextService() {
        return (IDbConnectionContextService) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {IDbConnectionContextService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "buildProfile" -> {
                        accountsSeen.add(currentAccount());
                        yield profile();
                    }
                    case "bindProfile" -> {
                        accountsSeen.add(currentAccount());
                        yield null;
                    }
                    default -> null;
                });
    }

    private IDbTableService noTables() {
        return (IDbTableService) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {IDbTableService.class},
                (proxy, method, args) -> "queryTables".equals(method.getName()) ? List.of() : null);
    }

    private static String currentAccount() {
        Context context = ContextUtils.queryContext();
        if (context == null || context.getLoginUser() == null) {
            return "(no account)";
        }
        return context.getLoginUser().getAccountName();
    }

    private static ConnectionProfile profile() {
        ConnectionProfile profile = new ConnectionProfile();
        profile.setDataSourceId(DATA_SOURCE_ID);
        profile.setDatabaseName("TopiaDB");
        profile.setSchemaName("dbo");
        return profile;
    }

    private static Context contextFor(String account) {
        LoginUser loginUser = new LoginUser();
        loginUser.setAccountName(account);
        return Context.builder().loginUser(loginUser).build();
    }

    private static AiListTablesRequest listTablesRequest(Context requestContext) {
        AiToolContextRequest toolContext = new AiToolContextRequest();
        toolContext.setDataSourceId(DATA_SOURCE_ID);
        toolContext.setDatabaseName("TopiaDB");
        toolContext.setSchemaName("dbo");
        // The web layer resolves the profile up front, on the request thread,
        // and puts it here - so the tool itself does not look it up a third time.
        toolContext.setConnectionProfile(profile());
        toolContext.setRequestContext(requestContext);

        AiListTablesRequest request = new AiListTablesRequest();
        request.setDataSourceId(DATA_SOURCE_ID);
        request.setDatabaseName("TopiaDB");
        request.setSchemaName("dbo");
        request.setAiToolContextRequest(toolContext);
        return request;
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
