package ai.chat2db.community.web.api.model.response.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An account as the management screen sees it. Never carries the password hash. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityUserResponse {

    private String username;

    private String role;

    private boolean enabled;

    private String createdAt;

    /** True for the one account that cannot be removed or demoted. */
    private boolean lastAdmin;
}
