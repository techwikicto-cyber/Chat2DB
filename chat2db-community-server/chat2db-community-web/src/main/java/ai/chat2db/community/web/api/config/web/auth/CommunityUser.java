package ai.chat2db.community.web.api.config.web.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A person who can sign in, and the owner of a workspace.
 *
 * The username is the identity in both senses: it is what is typed at sign-in,
 * and it is what the storage layer derives a workspace directory from, so
 * datasources, consoles and history belong to one account and are not visible to
 * any other - an admin included. The role decides who may manage accounts, not
 * who may see what.
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
