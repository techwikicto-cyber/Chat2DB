package ai.chat2db.community.web.api.config.web.auth;

/**
 * What an account may do inside this application.
 *
 * Deliberately two values. A role here governs the application's own screens,
 * not the databases behind it: both roles reach every stored connection with
 * whatever rights its credentials carry. Keeping someone away from production
 * data is a job for a read-only database account, not for this enum.
 */
public enum CommunityRole {

    /** May manage accounts, in addition to everything a USER can do. */
    ADMIN,

    /** May use the application. */
    USER
}
