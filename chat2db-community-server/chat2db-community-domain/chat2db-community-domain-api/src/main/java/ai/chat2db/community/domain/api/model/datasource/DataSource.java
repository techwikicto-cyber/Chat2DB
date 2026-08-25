package ai.chat2db.community.domain.api.model.datasource;

import ai.chat2db.community.domain.api.config.Environment;
import ai.chat2db.community.domain.api.config.DriverConfig;
import ai.chat2db.community.domain.api.model.datasource.KeyValue;
import ai.chat2db.community.domain.api.model.datasource.SSHInfo;
import ai.chat2db.community.domain.api.model.datasource.SSLInfo;
import java.util.List;
import lombok.Data;


@Data
public class DataSource {


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


    private Long organizationId;


    private String storageType;


    private Long spaceId;

    /**
     * How much of this connection's data the assistant may send to the model
     * provider: NONE, SAMPLE or FULL.
     *
     * <p>Blank on every connection made before the setting existed, which
     * reads as the default and so leaves those connections behaving exactly as
     * they did. See {@code AiDisclosurePolicyEnum}.
     */
    private String aiDisclosurePolicy;
}
