package ai.chat2db.community.storage.large;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.model.operation.Operation;
import com.google.common.collect.Lists;

import java.util.List;

public class ConsoleStorage extends LargeDataStorage<Operation> {

    private static final WorkspaceStorages<ConsoleStorage> WORKSPACES =
            new WorkspaceStorages<>(ConsoleStorage::new);

    /** The instance for the workspace of the request being served. */
    public static ConsoleStorage current() {
        return WORKSPACES.current();
    }

    protected ConsoleStorage(String basePath) {
        super("console", Operation.class, 10000, basePath);
    }

    public List<Operation> getDataList(Operation operation, int page, int pageSize) {
        List<Operation> list = getDataList();
        if (operation != null) {
           return operation.select(list, page, pageSize);
        }
        return Lists.newArrayList();
    }

}
