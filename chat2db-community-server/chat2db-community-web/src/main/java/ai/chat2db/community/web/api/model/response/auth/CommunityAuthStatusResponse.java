package ai.chat2db.community.web.api.model.response.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the interface needs to decide between the sign-in screen and the app.
 *
 * {@code required} is false only when sign-in has been switched off outright.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityAuthStatusResponse {

    private boolean required;

    private boolean authenticated;

    /** Null while signed out. */
    private String username;

    /** Null while signed out; "ADMIN" or "USER". */
    private String role;
}
