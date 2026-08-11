package ai.chat2db.community.tools.util;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * A deadline for statements run on behalf of something other than a person.
 *
 * <p>A query typed into the console is watched by whoever typed it: they can see
 * it running and stop it. A query written by the assistant has nobody watching.
 * It can ask for a full scan of a table with a billion rows in it, and until
 * this existed nothing in the application would have stopped it - the row limit
 * caps what comes back, not what the database is asked to do.
 *
 * <p>Held in a thread local because the statement is prepared several layers
 * below the code that knows where the SQL came from, and threading a parameter
 * through every execute method would touch a great deal that has no interest in
 * it. It lives here, in the module both the executor and the callers already
 * depend on, so that neither has to reach across into the other.
 *
 * <p>Always paired: {@link #runWithTimeout} sets and restores together, so a
 * pooled thread cannot inherit a deadline from whatever ran on it last.
 */
@Slf4j
public final class SqlExecutionLimits {

    private static final ThreadLocal<Integer> QUERY_TIMEOUT_SECONDS = new ThreadLocal<>();

    private SqlExecutionLimits() {
    }

    /**
     * Run something with a deadline on every statement it executes.
     *
     * @param timeoutSeconds seconds to allow; null or non-positive means no deadline.
     * @param action the work to run.
     * @return whatever the action returned.
     */
    public static <T> T runWithTimeout(Integer timeoutSeconds, Supplier<T> action) {
        Integer previous = QUERY_TIMEOUT_SECONDS.get();
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            QUERY_TIMEOUT_SECONDS.set(timeoutSeconds);
        } else {
            QUERY_TIMEOUT_SECONDS.remove();
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                QUERY_TIMEOUT_SECONDS.remove();
            } else {
                QUERY_TIMEOUT_SECONDS.set(previous);
            }
        }
    }

    /**
     * The deadline in force on this thread, or null when there is none.
     */
    public static Integer currentTimeoutSeconds() {
        return QUERY_TIMEOUT_SECONDS.get();
    }

    /**
     * Put the current deadline on a statement, if there is one.
     *
     * <p>A driver that does not support query timeouts throws rather than
     * quietly ignoring the request, and that is no reason to fail the query the
     * user asked for - so it is logged and the statement runs without one.
     *
     * @param statement the statement about to be executed.
     */
    public static void apply(Statement statement) {
        Integer timeoutSeconds = QUERY_TIMEOUT_SECONDS.get();
        if (statement == null || timeoutSeconds == null || timeoutSeconds <= 0) {
            return;
        }
        try {
            statement.setQueryTimeout(timeoutSeconds);
        } catch (SQLException | RuntimeException e) {
            log.warn("driver does not support a query timeout, running without one", e);
        }
    }
}
