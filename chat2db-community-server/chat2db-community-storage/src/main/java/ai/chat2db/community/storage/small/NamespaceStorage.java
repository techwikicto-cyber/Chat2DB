package ai.chat2db.community.storage.small;

import ai.chat2db.community.storage.WorkspaceStorages;
import ai.chat2db.community.domain.api.enums.NodeTypeEnum;
import ai.chat2db.community.domain.api.model.workspace.Namespace;
import ai.chat2db.community.domain.api.model.workspace.Node;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

public class NamespaceStorage extends SmallDataStorage<Namespace> {

    private static final WorkspaceStorages<NamespaceStorage> WORKSPACES =
            new WorkspaceStorages<>(NamespaceStorage::new);

    /** The instance for the workspace of the request being served. */
    public static NamespaceStorage current() {
        return WORKSPACES.current();
    }

    protected NamespaceStorage(String basePath) {
        super(basePath, "namespace", Namespace.class);
    }


    public void deleteDataSourcePosition(Long dataSourceId) {
        for (Namespace namespace : getDataList()) {
            List<Long> dataSourceIds = namespace.getDatasourceIds();
            if (!CollectionUtils.isEmpty(dataSourceIds)) {
                if (dataSourceIds.contains(dataSourceId)) {
                    dataSourceIds.remove(dataSourceId);
                }
            }
        }
        saveDataList();
    }

    public void updateDataSourcePosition(Long namespaceId, Long dataSourceId) {
        for (Namespace namespace : getDataList()) {
            List<Long> dataSourceIds = namespace.getDatasourceIds();
            if (!CollectionUtils.isEmpty(dataSourceIds)) {
                if (dataSourceIds.contains(dataSourceId)) {
                    dataSourceIds.remove(dataSourceId);
                }
            }
        }
        Namespace namespace = getById(namespaceId);
        if (namespace != null) {
            List<Long> dataSourceIds = namespace.getDatasourceIds();
            if (CollectionUtils.isEmpty(dataSourceIds)) {
                dataSourceIds = new ArrayList<>();
                dataSourceIds.add(dataSourceId);
                namespace.setDatasourceIds(dataSourceIds);
            } else {
                if (!dataSourceIds.contains(dataSourceId)) {
                    dataSourceIds.add(dataSourceId);
                }
            }
        }
        saveDataList();
    }

    public Long save(Namespace namespace){
        Long id = super.save(namespace);
        Node dropToNode = null;
        if (namespace.getParentId() != null) {
            dropToNode = new Node();
            dropToNode.setId(namespace.getParentId());
            dropToNode.setType(NodeTypeEnum.NAMESPACE.name());
        }
        Node node = new Node();
        node.setId(namespace.getId());
        node.setType(NodeTypeEnum.NAMESPACE.name());
        TreeNodeStorage.current().updatePosition(dropToNode, node, 2);
        return id;
    }
    public void delete(Long id) {
        super.delete(id);
        TreeNodeStorage.current().deleteNode(Node.builder().id(id).type(NodeTypeEnum.NAMESPACE.name()).build());
    }
}
