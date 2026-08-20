package ai.chat2db.community.storage;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.enums.StorageTypeEnum;
import ai.chat2db.community.domain.api.model.datasource.DataSource;
import ai.chat2db.community.domain.api.model.datasource.DataSourceNamespace;
import ai.chat2db.community.domain.api.model.er.ERPosition;
import ai.chat2db.community.domain.api.model.workspace.Namespace;
import ai.chat2db.community.domain.api.model.workspace.Node;
import ai.chat2db.community.domain.api.model.operation.Operation;
import ai.chat2db.community.domain.api.model.operation.OperationLog;
import ai.chat2db.community.domain.api.model.pin.PinTable;
import ai.chat2db.community.domain.api.model.task.Task;
import ai.chat2db.community.domain.api.model.request.pin.DbTablePinRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskRecordCreateRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskRecordPageRequest;
import ai.chat2db.community.domain.api.model.request.task.TaskRecordUpdateRequest;
import ai.chat2db.community.domain.api.model.request.datasource.DbDataSourcePageQueryRequest;
import ai.chat2db.community.domain.api.model.request.datasource.DbDataSourcePositionUpdateRequest;
import ai.chat2db.community.domain.api.model.request.operation.OpsOperationLogPageQueryRequest;
import ai.chat2db.community.domain.api.model.request.operation.OpsOperationPageQueryRequest;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorage;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSourceNamespace;
import ai.chat2db.community.storage.converter.StorageConverter;
import ai.chat2db.community.storage.large.ConsoleStorage;
import ai.chat2db.community.storage.large.OperationLogStorage;
import ai.chat2db.community.storage.large.TaskStorage;
import ai.chat2db.community.storage.small.*;
import ai.chat2db.community.tools.security.AesGcmUtil;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class LocalWorkspaceStorage implements IWorkspaceStorage {

    private final StorageConverter storageConverter;

    public LocalWorkspaceStorage(StorageConverter storageConverter) {
        this.storageConverter = storageConverter;
    }

    @Override
    public WorkspaceDataSource queryDataSourceById(Long id, Boolean requestPassword) {
        DataSource dataSource = DataSourceStorage.current().getById(id);
        if (dataSource == null) {
            return null;
        }
        WorkspaceDataSource result = storageConverter.dataSource2workspace(dataSource);
        result.setStorageType(StorageTypeEnum.LOCAL.name());
        if (!Boolean.TRUE.equals(requestPassword)) {
            result.setPassword(null);
        }
        return result;
    }

    @Override
    public Long createDataSource(WorkspaceDataSource dataSource) {
        dataSource.setStorageType(StorageTypeEnum.LOCAL.name());
        dataSource.setPassword(encryptString(dataSource.getPassword()));
        dataSource.setId(DataSourceStorage.current().generateId());
        Long id = DataSourceStorage.current().save(storageConverter.workspace2dataSource(dataSource));
        if (dataSource.getSpaceId() != null && dataSource.getSpaceId() > 0) {
            NamespaceStorage.current().updateDataSourcePosition(dataSource.getSpaceId(), id);
        }
        return id;
    }

    @Override
    public void deleteDataSource(Long id) {
        DataSourceStorage.current().delete(id);
    }

    @Override
    public Long updateDataSource(WorkspaceDataSource dataSource) {
        dataSource.setStorageType(StorageTypeEnum.LOCAL.name());
        if (dataSource.getPassword() != null && !dataSource.getPassword().isEmpty()) {
            dataSource.setPassword(encryptString(dataSource.getPassword()));
        } else {
            DataSource oldDataSource = DataSourceStorage.current().getById(dataSource.getId());
            dataSource.setPassword(oldDataSource == null ? null : oldDataSource.getPassword());
        }
        DataSourceStorage.current().update(storageConverter.workspace2dataSource(dataSource));
        return dataSource.getId();
    }

    @Override
    public PageResponse<WorkspaceDataSource> listDataSources(DbDataSourcePageQueryRequest dataSourcePageQueryRequest) {
        List<DataSource> dataSources = DataSourceStorage.current().getDataList();
        List<WorkspaceDataSource> result = storageConverter.dataSource2workspace(dataSources);
        result.forEach(dataSource -> dataSource.setStorageType(StorageTypeEnum.LOCAL.name()));
        return PageResponse.of(result, (long) result.size(), dataSourcePageQueryRequest.getPageNo(),
                dataSourcePageQueryRequest.getPageSize());
    }

    @Override
    public WorkspaceDataSourceNamespace getNamespaceDataSources() {
        DataResult<DataSourceNamespace> result = DataSourceStorage.current().getNamespaceDatasource();
        return result.getData() == null ? null : storageConverter.dataSourceNamespace2workspace(result.getData());
    }

    @Override
    public Long createNamespace(Namespace namespace) {
        return NamespaceStorage.current().save(namespace);
    }

    @Override
    public void updateNamespace(Namespace namespace) {
        NamespaceStorage.current().update(namespace);
    }

    @Override
    public void deleteNamespace(Long id) {
        NamespaceStorage.current().delete(id);
    }

    @Override
    public void updateDataSourcePosition(DbDataSourcePositionUpdateRequest updateDataSourcePositionRequest) {
        NamespaceStorage.current().updateDataSourcePosition(updateDataSourcePositionRequest.getNamespaceId(),
                updateDataSourcePositionRequest.getDataSourceId());
    }

    @Override
    public List<Node> getTree() {
        return DataSourceStorage.current().getNodes();
    }

    @Override
    public void updatePosition(Node dropToNode, Node dragNode, Integer dropPosition) {
        TreeNodeStorage.current().updatePosition(dropToNode, dragNode, dropPosition);
    }

    @Override
    public List<String> queryPinTables(DbTablePinRequest pinTableRequest) {
        PinTable pinTable = storageConverter.pinTableParam2model(pinTableRequest);
        return PinTableStorage.current().getPinTables(pinTable);
    }

    @Override
    public void pinTable(PinTable request) {
        PinTableStorage.current().save(request);
    }

    @Override
    public void deletePinTable(PinTable request) {
        PinTableStorage.current().delete(request);
    }

    @Override
    public PageResponse<Task> taskList(TaskRecordPageRequest taskPageRequest) {
        List<Task> tasks = TaskStorage.current().getDataList();
        return PageResponse.of(tasks, (long) tasks.size(), taskPageRequest.getPageNo(), taskPageRequest.getPageSize());
    }

    @Override
    public Task getTask(Long id) {
        return TaskStorage.current().getById(id);
    }

    @Override
    public Long createTask(TaskRecordCreateRequest taskCreateRequest) {
        Task task = storageConverter.taskCreateParam2model(taskCreateRequest);
        task.setId(TaskStorage.current().generateId());
        task.setGmtCreate(new Date());
        task.setGmtModified(new Date());
        TaskStorage.current().save(task);
        return task.getId();
    }

    @Override
    public void updateTask(TaskRecordUpdateRequest taskUpdateRequest) {
        Task task = storageConverter.taskUpdateParam2model(taskUpdateRequest);
        task.setGmtModified(new Date());
        TaskStorage.current().update(task);
    }

    @Override
    public Long createOperationLog(OperationLog request) {
        request.setGmtCreate(DateUtil.format(new Date(), DatePattern.NORM_DATETIME_PATTERN));
        request.setGmtModified(DateUtil.format(new Date(), DatePattern.NORM_DATETIME_PATTERN));
        return OperationLogStorage.current().save(request);
    }

    @Override
    public PageResponse<OperationLog> operationLogList(OpsOperationLogPageQueryRequest operationLogPageQueryRequest) {
        List<OperationLog> logs = OperationLogStorage.current().getDataList();
        return PageResponse.of(logs, (long) logs.size(), operationLogPageQueryRequest.getPageNo(),
                operationLogPageQueryRequest.getPageSize());
    }

    @Override
    public OperationLog getOperationLog(Long id) {
        return OperationLogStorage.current().getById(id);
    }

    @Override
    public PageResponse<Operation> consoleList(OpsOperationPageQueryRequest operationPageQueryRequest) {
        Operation operation = storageConverter.operationPageParam2model(operationPageQueryRequest);
        List<Operation> consoles = ConsoleStorage.current().getDataList(operation, operationPageQueryRequest.getPageNo(),
                operationPageQueryRequest.getPageSize());
        return PageResponse.of(consoles, (long) consoles.size(), operationPageQueryRequest.getPageNo(),
                operationPageQueryRequest.getPageSize());
    }

    @Override
    public Operation getConsole(Long id) {
        return ConsoleStorage.current().getById(id);
    }

    @Override
    public void deleteConsole(Long id) {
        ConsoleStorage.current().delete(id);
    }

    @Override
    public Long createConsole(Operation request) {
        return ConsoleStorage.current().save(request);
    }

    @Override
    public void updateConsole(Operation request) {
        ConsoleStorage.current().update(request);
    }

    @Override
    public String getErPosition(Long dataSourceId, String databaseName, String schemaName) {
        return ERPositionStorage.current().getPosition(dataSourceId, databaseName, schemaName);
    }

    @Override
    public void savePosition(ERPosition request) {
        ERPositionStorage.current().savePosition(request);
    }

    private String encryptString(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        return AesGcmUtil.configured().encrypt(password);
    }
}
