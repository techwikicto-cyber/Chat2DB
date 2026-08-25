package ai.chat2db.community.domain.api.model.datasource;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceConnect {




    private Boolean success;




    private String message;




    private String description;




    private String errorDetail;

    /**
     * Whether the account behind this connection was proved unable to write.
     *
     * <p>Three states, not two: CONFIRMED_READ_ONLY, CAN_WRITE, and
     * NOT_VERIFIED for the engines the probe leaves alone and the failures
     * it cannot read. Absent means the same as NOT_VERIFIED.
     */
    private String readOnlyVerdict;
}
