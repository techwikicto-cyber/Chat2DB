package ai.chat2db.community.domain.api.model.request.ai;

import ai.chat2db.community.domain.api.model.runtime.ConnectionProfile;
import ai.chat2db.community.tools.model.Context;
import jakarta.validation.Valid;
import lombok.Data;

@Data
public class AiToolContextRequest {

    private Long dataSourceId;

    private String databaseName;

    private String schemaName;

    /**
     * What the user actually asked, in their own words.
     *
     * <p>A tool is called with a statement, not with a question, so without
     * this the result checks can see that 200 rows came back but not that one
     * number was wanted. Read, never sent: it reaches the model already, as
     * the message that started the run.
     */
    private String question;

    @Valid
    private ConnectionProfile connectionProfile;

    @Valid
    private Context requestContext;
}
