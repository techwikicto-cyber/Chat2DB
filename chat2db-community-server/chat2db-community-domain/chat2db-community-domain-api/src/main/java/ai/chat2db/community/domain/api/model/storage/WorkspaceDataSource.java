package ai.chat2db.community.domain.api.model.storage;

import ai.chat2db.community.domain.api.config.Environment;
import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.datasource.KeyValue;
import ai.chat2db.community.domain.api.model.datasource.SSHInfo;
import ai.chat2db.community.domain.api.model.datasource.SSLInfo;
import lombok.Data;

import java.util.List;

@Data
public class WorkspaceDataSource {

    private Long id;

    private String alias;

    private String url;

    private String user;

    private String password;

    private String authenticationType;

    private String type;

    private String envType;

    private String host;

    private String port;

    private SSHInfo ssh;

    private SSLInfo ssl;

    private String sid;

    private String driver;

    private String jdbc;

    private List<KeyValue> extendInfo;

    private DriverConfig driverConfig;

    private Long environmentId;

    private Environment environment;

    private String kind;

    private String serviceName;

    private String serviceType;

    private boolean supportDatabase;

    private boolean supportSchema;

    private String email;

    private String keyfile;

    private String project;

    private Long organizationId;

    private String storageType;

    private Long spaceId;

    /** See {@code DataSource#aiDisclosurePolicy}: NONE, SAMPLE or FULL. */
    private String aiDisclosurePolicy;

    /**
     * What this database is, written by the person who owns it.
     *
     * <p>Free text. The schema says a column is called {@code Rank1} and holds
     * a float; only somebody who works here can say whether 1 is the best rank
     * or the worst. This is where they say it, and the assistant reads it
     * before answering anything about this connection.
     *
     * <p>Bounded, because it is sent with every question. See
     * {@code AiBusinessContextServiceImpl#MAX_PROFILE_CHARS}.
     */
    private String aiProfile;

    /**
     * Which physical table each library label points at, on this connection.
     *
     * <p>The library says {@code SUM({sales}.NetAmount)}; this says that
     * {@code sales} is {@code dbo.vw_Sales} here. Keeping the two apart is what
     * makes one definition usable at every customer built to the same shape -
     * the concept travels, the table does not.
     */
    private java.util.Map<String, String> aiBindings;
}
