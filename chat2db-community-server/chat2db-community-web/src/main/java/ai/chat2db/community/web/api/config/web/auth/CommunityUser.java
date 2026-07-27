package ai.chat2db.community.web.api.config.web.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A person who can sign in.
 *
 * Everyone shares the same datasources, consoles and history - nothing in the
 * storage model is scoped per user. An account buys individual credentials,
 * revocation of one person without disturbing the rest, and a name on the
 * operation log; it does not buy isolation.
 *
 * Unknown fields are ignored on read so a file written by a later version, with
 * fields this one does not know, still loads.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommunityUser {

    private String username;

    /** PBKDF2 hash, never the password. Withheld from every API response. */
    private String passwordHash;

    private CommunityRole role;

    /** A disabled account keeps its history but cannot sign in. */
    private boolean enabled;

    private String createdAt;

    public boolean isAdmin() {
        return role == CommunityRole.ADMIN;
    }
}
