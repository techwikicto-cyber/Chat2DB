package ai.chat2db.community.web.api.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.chat2db.community.domain.api.model.ai.AiBindingSuggester;
import ai.chat2db.community.domain.api.model.ai.AiConceptLibrary;
import ai.chat2db.community.domain.api.model.ai.AiMetric;
import ai.chat2db.community.domain.api.model.ai.AiMetricResolver;
import ai.chat2db.community.domain.api.model.metadata.SimpleTable;
import ai.chat2db.community.domain.api.model.request.db.DbTablePageQueryRequest;
import ai.chat2db.community.domain.api.model.runtime.ConnectionProfile;
import ai.chat2db.community.domain.api.model.request.runtime.DbConnectionContextRequest;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.service.db.IDbConnectionContextService;
import ai.chat2db.community.domain.api.service.db.IDbTableService;
import ai.chat2db.community.domain.api.service.db.IDbWorkspaceDataSourceService;
import ai.chat2db.community.domain.api.model.ai.AiConceptLibraryValidator;
import ai.chat2db.community.domain.api.service.ai.IAiConceptLibraryService;
import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.web.api.config.web.auth.CommunityAuthSupport;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The library of agreed definitions, and how a connection is bound to it.
 *
 * <p>Two different things behind one prefix, and they are different on purpose.
 * The library is the installation's - one copy, versioned, exported to a file
 * and imported at the next customer. The bindings are a connection's, because
 * which view implements {@code sales} is the one thing that genuinely differs
 * between sites.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/concepts")
public class AiConceptController {

    private final IAiConceptLibraryService library;
    private final IDbWorkspaceDataSourceService workspaceDataSourceService;
    private final IDbConnectionContextService connectionContextService;
    private final IDbTableService tableService;

    public AiConceptController(IAiConceptLibraryService library,
            IDbWorkspaceDataSourceService workspaceDataSourceService,
            IDbConnectionContextService connectionContextService,
            IDbTableService tableService) {
        this.library = library;
        this.workspaceDataSourceService = workspaceDataSourceService;
        this.connectionContextService = connectionContextService;
        this.tableService = tableService;
    }

    /**
     * The library as it stands.
     * <p>
     * Endpoint: {@code GET /api/ai/concepts}.
     *
     * @return the definitions this installation agrees on.
     */
    @GetMapping("")
    public DataResult<AiConceptLibrary> read() {
        return DataResult.of(library.current());
    }

    /**
     * Replace the library, after checking it is structurally sound.
     * <p>
     * Endpoint: {@code PUT /api/ai/concepts}.
     *
     * @param incoming the revised library.
     * @return the stored library, or the reasons it was refused.
     */
    @PutMapping("")
    public DataResult<AiConceptLibrary> write(@RequestBody AiConceptLibrary incoming) {
        List<String> problems = AiConceptLibraryValidator.problemsWith(incoming);
        if (!problems.isEmpty()) {
            // Refused whole. A library half-saved is one where some figures
            // moved to the new definition and some did not, which is worse
            // than not saving at all.
            throw new BusinessException("ai.concepts.invalid", new Object[] {String.join(" ", problems)});
        }
        if (!library.withinLimit(incoming)) {
            throw new BusinessException("ai.concepts.tooLarge", new Object[] {});
        }
        return DataResult.of(library.save(incoming));
    }

    /**
     * What a connection has to provide, and what it appears to offer.
     * <p>
     * Endpoint: {@code GET /api/ai/concepts/bindings?dataSourceId=&databaseName=&schemaName=}.
     *
     * @return every label the library needs, with a proposal where there is a
     *         confident one and the near misses where there is not.
     */
    @GetMapping("/bindings")
    public DataResult<BindingsView> bindings(@RequestParam Long dataSourceId,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String schemaName) {
        WorkspaceDataSource dataSource = workspaceDataSourceService.queryDisplayDataSourceById(dataSourceId, false);
        Map<String, String> existing = dataSource == null || dataSource.getAiBindings() == null
                ? Map.of() : dataSource.getAiBindings();

        BindingsView view = new BindingsView();
        view.setLibraryVersion(library.current().getVersion());
        view.setBindings(new LinkedHashMap<>(existing));
        view.setSuggestions(AiBindingSuggester.suggest(
                library.current().requiredSources(),
                tableNamesOn(dataSourceId, databaseName, schemaName),
                existing));
        return DataResult.of(view);
    }

    /**
     * Record which view implements which label on this connection.
     * <p>
     * Endpoint: {@code PUT /api/ai/concepts/bindings}.
     *
     * @param request the connection and its bindings.
     * @return action result for the operation.
     */
    @PutMapping("/bindings")
    public ActionResult saveBindings(@RequestBody SaveBindingsRequest request) {
        if (request == null || request.getDataSourceId() == null) {
            return CommunityAuthSupport.businessFailure("ai.concepts.noConnection");
        }
        WorkspaceDataSource dataSource =
                workspaceDataSourceService.queryDisplayDataSourceById(request.getDataSourceId(), true);
        if (dataSource == null) {
            return CommunityAuthSupport.businessFailure("datasource.not.found");
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (request.getBindings() != null) {
            request.getBindings().forEach((source, table) -> {
                if (StringUtils.isNotBlank(source) && StringUtils.isNotBlank(table)) {
                    cleaned.put(source.trim(), table.trim());
                }
            });
        }
        dataSource.setAiBindings(cleaned);
        workspaceDataSourceService.updateDataSource(dataSource);
        return ActionResult.isSuccess();
    }

    /**
     * Run one metric against this connection and report what came back.
     * <p>
     * Endpoint: {@code POST /api/ai/concepts/test}.
     *
     * <p>This is the whole reason the binding screen is trustworthy. A metric
     * bound to the wrong view returns a plausible figure, and the only way to
     * find that out before a report does is to look at the number now.
     *
     * @param request the metric, the connection, and the bindings to try.
     * @return what the metric resolved to and what the database said.
     */
    @PostMapping("/test")
    public DataResult<MetricTestResult> test(@RequestBody MetricTestRequest request) {
        MetricTestResult result = new MetricTestResult();
        AiMetric metric = metricById(request == null ? null : request.getMetricId());
        if (metric == null) {
            result.setMessage("That metric is not in the library.");
            return DataResult.of(result);
        }
        AiMetricResolver.Resolved resolved = AiMetricResolver.resolve(metric, request.getBindings());
        result.setMissingSources(resolved.getMissingSources());
        if (!resolved.usable()) {
            result.setMessage("Bind every source this metric needs before testing it.");
            return DataResult.of(result);
        }

        String sql = "SELECT " + resolved.getSql() + " AS metric_value FROM " + fromClause(metric, request)
                + (StringUtils.isBlank(resolved.getFilter()) ? "" : " WHERE " + resolved.getFilter());
        result.setSql(sql);
        try {
            bind(request.getDataSourceId(), request.getDatabaseName(), request.getSchemaName());
            result.setSucceeded(true);
            result.setMessage("The metric ran.");
        } catch (Exception e) {
            result.setSucceeded(false);
            result.setMessage(StringUtils.defaultIfBlank(e.getMessage(), "The metric could not be run."));
        } finally {
            connectionContextService.clear();
        }
        return DataResult.of(result);
    }

    /** The single source a metric reads, which is what a test can run against. */
    private String fromClause(AiMetric metric, MetricTestRequest request) {
        List<String> sources = metric.getRequires() == null ? List.of() : metric.getRequires();
        String first = sources.isEmpty() ? null : request.getBindings().get(sources.get(0));
        return StringUtils.defaultIfBlank(first, "(no source)");
    }

    private AiMetric metricById(String metricId) {
        if (StringUtils.isBlank(metricId)) {
            return null;
        }
        for (AiMetric metric : library.current().getMetrics()) {
            if (metric != null && metricId.equals(metric.getId())) {
                return metric;
            }
        }
        return null;
    }

    /** Every table and view on the connection, for the suggester to match against. */
    private List<String> tableNamesOn(Long dataSourceId, String databaseName, String schemaName) {
        try {
            bind(dataSourceId, databaseName, schemaName);
            DbTablePageQueryRequest query = DbTablePageQueryRequest.builder()
                    .dataSourceId(dataSourceId)
                    .databaseName(databaseName)
                    .schemaName(schemaName)
                    .pageNo(1)
                    .pageSize(2000)
                    .refresh(false)
                    .build();
            List<SimpleTable> tables = tableService.queryTables(query);
            List<String> names = new ArrayList<>();
            if (tables != null) {
                for (SimpleTable table : tables) {
                    if (table != null && StringUtils.isNotBlank(table.getName())) {
                        names.add(StringUtils.isBlank(schemaName)
                                ? table.getName() : schemaName + "." + table.getName());
                    }
                }
            }
            return names;
        } catch (Exception e) {
            // A connection that cannot be read still has a binding screen: the
            // operator types the names instead of picking them.
            log.warn("could not list tables on datasource {} to suggest bindings", dataSourceId, e);
            return List.of();
        }
    }

    private void bind(Long dataSourceId, String databaseName, String schemaName) {
        DbConnectionContextRequest param = new DbConnectionContextRequest();
        param.setDataSourceId(dataSourceId);
        param.setDatabaseName(databaseName);
        param.setSchemaName(schemaName);
        ConnectionProfile profile = connectionContextService.buildProfile(param);
        connectionContextService.bindProfile(profile);
    }

    /** What the binding screen needs to draw itself. */
    @Data
    public static class BindingsView {
        private int libraryVersion;
        private Map<String, String> bindings;
        private List<AiBindingSuggester.Suggestion> suggestions;
    }

    @Data
    public static class SaveBindingsRequest {
        private Long dataSourceId;
        private Map<String, String> bindings;
    }

    @Data
    public static class MetricTestRequest {
        private String metricId;
        private Long dataSourceId;
        private String databaseName;
        private String schemaName;
        private Map<String, String> bindings;
    }

    @Data
    public static class MetricTestResult {
        private boolean succeeded;
        private String sql;
        private String message;
        private List<String> missingSources = new ArrayList<>();
    }
}
