package ai.chat2db.community.web.api.model.request.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Creating or amending an account from the management screen. */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityUserSaveRequest {

    private String username;

    /** Required when creating, optional when amending: blank leaves it unchanged. */
    private String password;

    /** "ADMIN" or "USER". */
    private String role;

    private Boolean enabled;
}
