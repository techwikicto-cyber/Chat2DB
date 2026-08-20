package ai.chat2db.community.storage.small;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.model.chart.Chart;

public class ChartStorage extends SmallDataStorage<Chart> {

    private static final WorkspaceStorages<ChartStorage> WORKSPACES =
            new WorkspaceStorages<>(ChartStorage::new);

    /** The instance for the workspace of the request being served. */
    public static ChartStorage current() {
        return WORKSPACES.current();
    }

    protected ChartStorage(String basePath) {
        super(basePath, "chart", Chart.class);
    }
}
