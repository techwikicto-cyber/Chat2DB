package ai.chat2db.community.storage.small;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.model.chart.Dashboard;

import java.util.Comparator;
import java.util.List;

public class DashboardStorage extends SmallDataStorage<Dashboard> {

    private static final WorkspaceStorages<DashboardStorage> WORKSPACES =
            new WorkspaceStorages<>(DashboardStorage::new);

    /** The instance for the workspace of the request being served. */
    public static DashboardStorage current() {
        return WORKSPACES.current();
    }

    protected DashboardStorage(String basePath) {
        super(basePath, "dashboard", Dashboard.class);
    }

    @Override
    public List<Dashboard> getDataList() {
        return super.getDataList().stream()
                .sorted(Comparator.comparing(Dashboard::getGmtModified,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
