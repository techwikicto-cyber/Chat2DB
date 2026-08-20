package ai.chat2db.community.storage.large;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.model.task.Task;

public class TaskStorage extends LargeDataStorage<Task> {
    private static final WorkspaceStorages<TaskStorage> WORKSPACES =
            new WorkspaceStorages<>(TaskStorage::new);

    /** The instance for the workspace of the request being served. */
    public static TaskStorage current() {
        return WORKSPACES.current();
    }
    protected TaskStorage(String basePath) {
        super("task", Task.class, 20, basePath);
    }
}
