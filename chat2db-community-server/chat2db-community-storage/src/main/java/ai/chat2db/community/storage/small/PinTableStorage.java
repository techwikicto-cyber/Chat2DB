package ai.chat2db.community.storage.small;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.model.pin.PinTable;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PinTableStorage extends SmallDataStorage<PinTable> {

    private static final WorkspaceStorages<PinTableStorage> WORKSPACES =
            new WorkspaceStorages<>(PinTableStorage::new);

    /** The instance for the workspace of the request being served. */
    public static PinTableStorage current() {
        return WORKSPACES.current();
    }

    protected PinTableStorage(String basePath) {
        super(basePath, "pin_table", PinTable.class);
    }

    public void delete(PinTable pinTable) {
        List<PinTable> pinTables = getDataList();
        if (CollectionUtils.isNotEmpty(pinTables)) {
            for (PinTable table : pinTables) {
                if (table == null) {
                    continue;
                }
                if (table.equals(pinTable)) {
                    pinTables.remove(table);
                    delete(table.getId());
                    break;
                }
            }
        }
    }
    public List<String> getPinTables(PinTable pinTable) {
        List<PinTable> result = new ArrayList<>();
        if(pinTable == null ){
            return Lists.newArrayList();
        }
        List<PinTable> pinTables = getDataList();
        if (CollectionUtils.isNotEmpty(pinTables)) {
            for (PinTable table : pinTables) {
                if (table == null) {
                    continue;
                }
                if (table.select(pinTable)) {
                    result.add(table);
                }
            }
        }
        Collections.reverse(result);
        return result.stream().map(PinTable::getTableName).collect(java.util.stream.Collectors.toList());
    }

}
