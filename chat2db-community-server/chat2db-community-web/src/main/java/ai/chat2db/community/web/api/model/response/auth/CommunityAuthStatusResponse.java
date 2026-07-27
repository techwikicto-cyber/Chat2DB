package ai.chat2db.community.web.api.model.response.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the interface needs to decide between the sign-in screen and the app.
 *
 * {@code required} is false when no shared password is configured, which is the
 * default and leaves the deployment open exactly as before.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityAuthStatusResponse {

    private boolean required;

    private boolean authenticated;
}
