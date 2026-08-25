package ai.chat2db.spi.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Whether the account behind a connection can actually write.
 *
 * <p>Every piece of safety built around the assistant assumes the connection
 * is read-only, and nothing has ever checked. A user told to "use a read-only
 * account" who pastes in the same admin credentials they use everywhere gets
 * the same green tick as a user who did it properly, and finds out which one
 * they were the first time something goes wrong.
 *
 * <p>The only honest way to know is to try. So the probe attempts a write and
 * takes the refusal as the answer.
 *
 * <h2>What it will and will not touch</h2>
 *
 * <p>The write has to be one that leaves nothing behind even when it
 * <em>succeeds</em>, and that is engine-specific rather than universal:
 *
 * <ul>
 *   <li><b>PostgreSQL</b> - a temporary table is transactional, so the
 *       rollback removes it.</li>
 *   <li><b>MySQL</b> and <b>SQL Server</b> - a temporary table is scoped to
 *       the session, and the probe's connection is closed immediately after.
 *       It is dropped explicitly as well.</li>
 *   <li><b>Oracle</b> and everything else - <b>nothing is executed at all</b>.
 *       A global temporary table in Oracle is real DDL: it commits
 *       implicitly and leaves a permanent object in the user's schema. A probe
 *       that creates a permanent object in a production database to check
 *       whether it is allowed to is worse than not knowing, so on those
 *       engines this reports {@link Verdict#NOT_VERIFIED} without opening its
 *       mouth.</li>
 * </ul>
 *
 * <h2>Which way it errs</h2>
 *
 * <p>Only two outcomes are asserted. A write that <em>succeeded</em> proves
 * the account can write. A write refused with a privilege error proves it
 * cannot. Everything else - a timeout, a quota, a syntax the driver did not
 * like, a disabled probe - is {@link Verdict#NOT_VERIFIED}, and a
 * not-verified is reported as not verified rather than dressed up as safe.
 * The one claim that must never be made falsely is "read-only confirmed",
 * because it is the one somebody would act on.
 */
public final class ReadOnlyProbe {

    public enum Verdict {
        /** The account tried to write and was refused. */
        CONFIRMED_READ_ONLY,
        /** The account wrote successfully. Whatever it made has been removed. */
        CAN_WRITE,
        /** No answer: not attempted on this engine, switched off, or inconclusive. */
        NOT_VERIFIED
    }

    /** Named so that anything it ever leaves behind is obviously ours. */
    static final String PROBE_TABLE = "chat2db_readonly_probe";

    /** Engines where a temporary table costs nothing and cleans itself up. */
    private static final Set<String> PROBEABLE = Set.of("POSTGRESQL", "MYSQL", "SQLSERVER");

    /**
     * SQLStates that mean "you are not allowed", as opposed to "that did not
     * work". 42501 is the standard insufficient-privilege state; 28000 is
     * invalid authorization; 42000 is what MySQL returns for a denied CREATE.
     */
    private static final Set<String> PRIVILEGE_STATES = Set.of("42501", "28000", "42000");

    /**
     * The words drivers use when the answer is really "no". A fallback for the
     * drivers that report a privilege refusal under a generic SQLState.
     */
    private static final String[] PRIVILEGE_WORDS = {
            "permission denied", "denied to user", "access denied", "insufficient privilege",
            "not have permission", "no permission", "privilege not held", "must be owner",
            "read-only", "read only"};

    private ReadOnlyProbe() {
    }

    /**
     * Attempt one write on an already-open connection and report what happened.
     *
     * <p>Never throws, and never leaves the connection's autocommit setting
     * different from how it found it. A caller that ignores the result is in
     * exactly the position it was in before this existed.
     */
    public static Verdict probe(Connection connection, String dbType) {
        if (connection == null || !enabled() || !isProbeable(dbType)) {
            return Verdict.NOT_VERIFIED;
        }
        String create = createStatementFor(dbType);
        if (create == null) {
            return Verdict.NOT_VERIFIED;
        }

        final boolean autoCommitWas;
        try {
            autoCommitWas = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (Exception cannotControlTransaction) {
            return Verdict.NOT_VERIFIED;
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(create);
            // It worked, so the account can write. Take it back out on the
            // engines where a rollback would not.
            quietly(() -> statement.execute(dropStatementFor(dbType)));
            return Verdict.CAN_WRITE;
        } catch (SQLException refused) {
            return refusedForPrivilege(refused) ? Verdict.CONFIRMED_READ_ONLY : Verdict.NOT_VERIFIED;
        } catch (Exception anythingElse) {
            return Verdict.NOT_VERIFIED;
        } finally {
            quietly(connection::rollback);
            quietly(() -> connection.setAutoCommit(autoCommitWas));
        }
    }

    /**
     * Whether a refusal was about permission rather than about anything else.
     *
     * <p>Package-private because this is the judgement worth testing, and the
     * JDBC round trip around it is not something a unit test can have.
     */
    static boolean refusedForPrivilege(SQLException refused) {
        if (refused == null) {
            return false;
        }
        for (Throwable cause = refused; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && PRIVILEGE_STATES.contains(
                    StringUtils.trimToEmpty(sql.getSQLState()))) {
                return true;
            }
            String message = StringUtils.lowerCase(cause.getMessage(), Locale.ROOT);
            if (message != null) {
                for (String word : PRIVILEGE_WORDS) {
                    if (message.contains(word)) {
                        return true;
                    }
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    static boolean isProbeable(String dbType) {
        return PROBEABLE.contains(StringUtils.upperCase(StringUtils.trimToEmpty(dbType), Locale.ROOT));
    }

    static String createStatementFor(String dbType) {
        return switch (StringUtils.upperCase(StringUtils.trimToEmpty(dbType), Locale.ROOT)) {
            // Transactional here: the rollback in `finally` removes it.
            case "POSTGRESQL" -> "CREATE TEMPORARY TABLE " + PROBE_TABLE + " (probe INT)";
            // Session-scoped: dropped below, and gone with the connection either way.
            case "MYSQL" -> "CREATE TEMPORARY TABLE " + PROBE_TABLE + " (probe INT)";
            // The leading # is what makes it local to this session, in tempdb.
            case "SQLSERVER" -> "CREATE TABLE #" + PROBE_TABLE + " (probe INT)";
            default -> null;
        };
    }

    static String dropStatementFor(String dbType) {
        return switch (StringUtils.upperCase(StringUtils.trimToEmpty(dbType), Locale.ROOT)) {
            case "POSTGRESQL", "MYSQL" -> "DROP TABLE IF EXISTS " + PROBE_TABLE;
            case "SQLSERVER" -> "DROP TABLE IF EXISTS #" + PROBE_TABLE;
            default -> null;
        };
    }

    /**
     * The off switch, for an operator who would rather this never ran.
     *
     * <p>It exists because the probe writes, however briefly, and somebody
     * running against a database under audit is entitled to say no to that
     * without giving a reason.
     */
    static boolean enabled() {
        String configured = System.getenv("CHAT2DB_READONLY_PROBE");
        return !StringUtils.equalsAnyIgnoreCase(StringUtils.trimToEmpty(configured), "off", "false", "0", "no");
    }

    private interface Fallible {
        void run() throws Exception;
    }

    private static void quietly(Fallible action) {
        try {
            action.run();
        } catch (Exception ignored) {
            // Cleanup and restoration are best effort by definition: the probe
            // has already learned what it came for.
        }
    }
}
