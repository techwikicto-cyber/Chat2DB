package ai.chat2db.community.domain.core.impl.operation;

import java.util.List;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.model.operation.OperationLog;
import ai.chat2db.community.domain.api.model.request.operation.OpsOperationLogPageQueryRequest;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorageFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the execution log's list preview.
 *
 * <p>The list shows a shortened statement so the panel stays scannable, and the
 * detail endpoint is where the whole one comes from. The local store keeps every
 * record in memory and returns the list as references to those objects, so
 * shortening one for display used to shorten the record itself: after the panel
 * had been opened once, the statement was gone everywhere - the detail endpoint,
 * the console tab opened from the log, and the file once anything rewrote it.
 * These tests hold the line that a read never alters what was read.
 */
class OpsOperationLogQueryServiceImplPreviewTest {

    private static final int PREVIEW_LENGTH = 200;

    private static OperationLog log(Long id, String ddl) {
        OperationLog operationLog = new OperationLog();
        operationLog.setId(id);
        operationLog.setDdl(ddl);
        operationLog.setDataSourceName("ChatServer");
        return operationLog;
    }

    private static String statementOfLength(int length) {
        StringBuilder builder = new StringBuilder("SELECT ");
        while (builder.length() < length) {
            builder.append("column_name, ");
        }
        return builder.substring(0, length);
    }

    /** A store that hands out its own objects, exactly as the local one does. */
    private static OpsOperationLogQueryServiceImpl serviceOver(List<OperationLog> stored) {
        IWorkspaceStorageFacade storage = (IWorkspaceStorageFacade) java.lang.reflect.Proxy.newProxyInstance(
            IWorkspaceStorageFacade.class.getClassLoader(),
            new Class<?>[] {IWorkspaceStorageFacade.class},
            (proxy, method, args) -> {
                if ("operationLogList".equals(method.getName())) {
                    return PageResponse.of(stored, (long) stored.size(), 1, 40);
                }
                if ("getOperationLog".equals(method.getName())) {
                    return stored.stream().filter(item -> item.getId().equals(args[0])).findFirst().orElse(null);
                }
                return null;
            });
        return new OpsOperationLogQueryServiceImpl(storage);
    }

    @Test
    void listingDoesNotShortenTheStoredStatement() {
        String full = statementOfLength(PREVIEW_LENGTH + 120);
        OperationLog stored = log(1L, full);
        OpsOperationLogQueryServiceImpl service = serviceOver(List.of(stored));

        service.operationLogPreviewList(new OpsOperationLogPageQueryRequest());

        assertEquals(full, stored.getDdl(), "the stored record must be untouched by a list call");
        assertFalse(stored.getMore(), "the stored record must not be flagged as a preview");
        assertEquals(full, service.getOperationLog(1L).getDdl(), "the detail endpoint must still return all of it");
    }

    @Test
    void listingStillReturnsAShortenedCopyFlaggedAsSuch() {
        String full = statementOfLength(PREVIEW_LENGTH + 120);
        OperationLog stored = log(1L, full);
        OpsOperationLogQueryServiceImpl service = serviceOver(List.of(stored));

        OperationLog listed = service.operationLogPreviewList(new OpsOperationLogPageQueryRequest()).getData().get(0);

        assertNotSame(stored, listed, "a shortened row has to be a copy, not the record");
        assertEquals(full.substring(0, PREVIEW_LENGTH) + "...", listed.getDdl());
        assertTrue(listed.getMore(), "the client fetches the rest only when this says there is more");
        assertEquals("ChatServer", listed.getDataSourceName(), "the copy keeps the rest of the record");
    }

    @Test
    void aShortStatementIsPassedThroughWhole() {
        OperationLog stored = log(1L, "select count(*) from public_chat");
        OpsOperationLogQueryServiceImpl service = serviceOver(List.of(stored));

        OperationLog listed = service.operationLogPreviewList(new OpsOperationLogPageQueryRequest()).getData().get(0);

        assertSame(stored, listed, "nothing to shorten, so nothing to copy");
        assertEquals("select count(*) from public_chat", listed.getDdl());
        assertFalse(listed.getMore());
    }

    @Test
    void aStatementExactlyAtTheLimitIsNotShortened() {
        String exact = statementOfLength(PREVIEW_LENGTH);
        OperationLog stored = log(1L, exact);
        OpsOperationLogQueryServiceImpl service = serviceOver(List.of(stored));

        OperationLog listed = service.operationLogPreviewList(new OpsOperationLogPageQueryRequest()).getData().get(0);

        assertEquals(exact, listed.getDdl());
        assertFalse(listed.getMore());
    }
}
