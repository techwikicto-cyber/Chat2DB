package ai.chat2db.community.domain.core.impl.ai;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import ai.chat2db.community.domain.api.model.ai.AiToolFailures;
import ai.chat2db.community.tools.exception.ConnectionException;
import org.apache.commons.lang3.StringUtils;

/**
 * Telling "the database is not there" apart from "that query was wrong".
 *
 * <p>Both arrive at the assistant as a failed tool call carrying a string, and
 * before this it could not tell them apart - so it did what a model does with
 * an unexplained failure and wrote around it:
 *
 * <blockquote>متأسفم، در حال حاضر نمی‌توانم به جدول Transactions وصل شوم و
 * داده‌های واقعی را استخراج کنم... اگر می‌توانید جزئیات ساختار را به‌صورت متنی
 * در اختیارم بگذارید...</blockquote>
 *
 * <p>Read that as a user and the most natural conclusion is that the assistant
 * is not up to the question. The actual news - your database is unreachable
 * right now - is the one thing the paragraph does not say, and it is the only
 * thing the reader could have acted on.
 *
 * <p>So a connectivity failure is now named as one. The tool result says what
 * happened, and says it is not the model's limitation and not the question's
 * fault; and the same finding is raised to the interface separately, so the
 * user learns it from the product rather than from a paragraph the model
 * composed.
 *
 * <p>The classification is deliberately narrow. Calling a genuine SQL mistake
 * "the database is unreachable" would send somebody to check their network
 * over a mistyped column name, so anything that is not recognisably about
 * reaching the server is left alone and handled as it was before.
 */
public final class AiDatabaseReachability {

    /**
     * The prefix the web layer watches for.
     *
     * <p>A marker rather than a guess at the prose, because the interface has
     * to recognise this without parsing English and without being coupled to
     * the wording the model is given. Declared in the api module, which is the
     * only one both the writer here and the reader in the web layer can see.
     */
    public static final String MARKER = AiToolFailures.DATABASE_UNREACHABLE;

    /**
     * SQLState classes that are about the connection, not the statement.
     *
     * <p>08 is "connection exception" in the standard. 28 is invalid
     * authorization - credentials that no longer work are a connection the
     * user has to go and fix, and telling them the query was wrong would send
     * them looking in the wrong place entirely.
     */
    private static final List<String> CONNECTION_SQL_STATE_CLASSES = List.of("08", "28");

    /** How drivers and pools say it when the server cannot be reached. */
    private static final List<String> CONNECTION_PHRASES = List.of(
            "connection refused", "connection reset", "connection timed out", "connect timed out",
            "connection is closed", "connection closed", "closed connection", "no connection",
            "connection not available", "connection is not available", "unable to acquire",
            "communications link failure", "broken pipe", "socket", "network is unreachable",
            "no route to host", "unknownhost", "unknown host", "name or service not known",
            "the tcp/ip connection to the host", "could not open connection",
            "login failed for user", "password authentication failed", "access denied for user",
            "connection.error", "connection.ssh.error", "sshexception", "jschexception",
            "server closed the connection", "terminating connection", "shutdown in progress",
            "too many connections", "pool", "i/o error", "io error");

    /**
     * Phrases that mean the server answered and disliked the statement.
     *
     * <p>Checked first, because several of them contain words the list above
     * also matches - a "permission denied" on one table is not an
     * authorisation failure on the connection, and "invalid object name" from
     * a server that just replied is proof the server is reachable.
     */
    private static final List<String> STATEMENT_PHRASES = List.of(
            "syntax error", "incorrect syntax", "invalid column", "unknown column",
            "invalid object name", "does not exist", "doesn't exist", "no such table",
            "no such column", "ambiguous column", "ora-00904", "ora-00942",
            "group by", "order by", "divide by zero", "arithmetic overflow",
            "conversion failed", "data type", "cannot be converted",
            "datasource.not.found", "refused by the sql guard", "duplicate column");

    private AiDatabaseReachability() {
    }

    /**
     * The tool result to return when a failure was about reaching the database.
     *
     * @return the report, or {@code null} when the failure was something else
     *         and should be handled as it always was.
     */
    public static String unreachableReport(Throwable failure) {
        return isUnreachable(failure) ? report(describe(failure)) : null;
    }

    /** As above, for the failures that arrive as a message rather than as a throw. */
    public static String unreachableReport(String errorMessage) {
        return isUnreachable(errorMessage) ? report(StringUtils.trimToEmpty(errorMessage)) : null;
    }

    static String report(String detail) {
        return MARKER + " the database could not be reached, so the query did not run."
                + (StringUtils.isBlank(detail) ? "" : " The driver reported: " + detail)
                + "\n\nThis is a connection failure. It is not a limit of your abilities and there is "
                + "nothing wrong with the question. Tell the user, in their own language, that the "
                + "database cannot be reached at the moment and that nothing was read from it. "
                + "Do not apologise for what you cannot do, do not offer to write the query anyway, "
                + "and do not ask them to paste the schema or the column names - none of that is the "
                + "problem. Say it plainly and stop.";
    }

    static boolean isUnreachable(Throwable failure) {
        if (failure == null) {
            return false;
        }
        for (Throwable cause = failure; cause != null; cause = nextCause(cause)) {
            if (cause instanceof ConnectionException) {
                return true;
            }
            if (cause instanceof SQLException sqlFailure && isConnectionSqlState(sqlFailure.getSQLState())) {
                return true;
            }
            if (matchesStatementPhrase(cause.getMessage())) {
                // The server replied and objected. Whatever else is in the
                // chain, this connection is up.
                return false;
            }
            if (matchesConnectionPhrase(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }

    static boolean isUnreachable(String errorMessage) {
        return !matchesStatementPhrase(errorMessage) && matchesConnectionPhrase(errorMessage);
    }

    /** What the driver said, trimmed to something a person can read. */
    static String describe(Throwable failure) {
        Throwable deepest = failure;
        for (Throwable cause = failure; cause != null; cause = nextCause(cause)) {
            if (StringUtils.isNotBlank(cause.getMessage())) {
                deepest = cause;
            }
        }
        String message = StringUtils.trimToEmpty(deepest == null ? null : deepest.getMessage());
        String firstLine = message.isEmpty() ? "" : message.split("\\R", 2)[0];
        return firstLine.length() > 240 ? firstLine.substring(0, 237) + "..." : firstLine;
    }

    private static boolean isConnectionSqlState(String sqlState) {
        String state = StringUtils.trimToEmpty(sqlState);
        return state.length() >= 2 && CONNECTION_SQL_STATE_CLASSES.contains(state.substring(0, 2));
    }

    private static boolean matchesConnectionPhrase(String message) {
        return containsAny(message, CONNECTION_PHRASES);
    }

    private static boolean matchesStatementPhrase(String message) {
        return containsAny(message, STATEMENT_PHRASES);
    }

    private static boolean containsAny(String message, List<String> phrases) {
        if (StringUtils.isBlank(message)) {
            return false;
        }
        String lowered = message.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (lowered.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    /** Guards the cause chains that some drivers make circular. */
    private static Throwable nextCause(Throwable current) {
        Throwable cause = current.getCause();
        return cause == current ? null : cause;
    }
}
