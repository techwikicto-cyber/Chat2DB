package ai.chat2db.community.domain.core.impl.operation;

import ai.chat2db.community.domain.api.model.PageResponse;
import ai.chat2db.community.domain.api.model.operation.OperationLog;
import ai.chat2db.community.domain.api.model.request.operation.OpsOperationLogPageQueryRequest;
import ai.chat2db.community.domain.api.service.ops.IOpsOperationLogQueryService;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorageFacade;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class OpsOperationLogQueryServiceImpl implements IOpsOperationLogQueryService {

    private static final int DDL_PREVIEW_LENGTH = 200;

    private final IWorkspaceStorageFacade workspaceStorageFacade;

    public OpsOperationLogQueryServiceImpl(IWorkspaceStorageFacade workspaceStorageFacade) {
        this.workspaceStorageFacade = workspaceStorageFacade;
    }

    @Override
    public PageResponse<OperationLog> operationLogList(OpsOperationLogPageQueryRequest request) {
        return workspaceStorageFacade.operationLogList(request);
    }

    @Override
    public PageResponse<OperationLog> operationLogPreviewList(OpsOperationLogPageQueryRequest request) {
        PageResponse<OperationLog> page = operationLogList(request);
        if (CollectionUtils.isNotEmpty(page.getData())) {
            page.setData(page.getData().stream().map(this::toPreview).collect(Collectors.toList()));
        }
        return page;
    }

    @Override
    public OperationLog getOperationLog(Long id) {
        return workspaceStorageFacade.getOperationLog(id);
    }

    @Override
    public Long createOperationLog(OperationLog request) {
        return workspaceStorageFacade.createOperationLog(request);
    }

    /**
     * The list carries a shortened statement; the detail endpoint carries the whole one.
     *
     * <p>Returns a copy whenever there is something to shorten, and this matters
     * more than it looks. The local store keeps every record in memory and hands
     * the list back as references to those very objects, so shortening one in
     * place did not shorten a response - it shortened the record. From then on
     * {@link #getOperationLog(Long)} read the same truncated object, the console
     * tab opened from the log showed a statement ending in "...", and the next
     * rewrite of the file would have put the truncation on disk. Merely opening
     * the panel destroyed the thing it was showing.
     *
     * @param operationLog the stored record, which must not be modified.
     * @return the record itself when it is short enough, otherwise a shortened copy.
     */
    private OperationLog toPreview(OperationLog operationLog) {
        if (operationLog == null || StringUtils.isBlank(operationLog.getDdl())
                || operationLog.getDdl().length() <= DDL_PREVIEW_LENGTH) {
            return operationLog;
        }
        OperationLog preview = JSON.parseObject(JSON.toJSONString(operationLog), OperationLog.class);
        preview.setDdl(operationLog.getDdl().substring(0, DDL_PREVIEW_LENGTH) + "...");
        preview.setMore(Boolean.TRUE);
        return preview;
    }
}
