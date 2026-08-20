package ai.chat2db.community.storage.large;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.model.operation.OperationLog;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;

public class OperationLogStorage extends LargeDataStorage<OperationLog> {

    private static final WorkspaceStorages<OperationLogStorage> WORKSPACES =
            new WorkspaceStorages<>(OperationLogStorage::new);

    /** The instance for the workspace of the request being served. */
    public static OperationLogStorage current() {
        return WORKSPACES.current();
    }

    protected OperationLogStorage(String basePath) {
        super("operation_log", OperationLog.class, 1000, basePath);
    }

    @Override
    public List<OperationLog> getDataList() {
        List<OperationLog> list = super.getDataList();
        if (CollectionUtils.isNotEmpty(list)) {
            Collections.reverse(list);
        }
        return list;
    }

}
