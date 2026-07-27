package ai.chat2db.community.web.api.model.request.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Changing one's own password; the current one is required to prove it is you. */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPasswordChangeRequest {

    private String currentPassword;

    private String newPassword;
}
