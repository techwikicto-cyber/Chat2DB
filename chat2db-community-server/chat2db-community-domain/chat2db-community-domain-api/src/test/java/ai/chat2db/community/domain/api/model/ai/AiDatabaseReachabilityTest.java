package ai.chat2db.community.domain.api.model.ai;

import java.sql.SQLException;

import ai.chat2db.community.tools.exception.BusinessException;
import ai.chat2db.community.tools.exception.ConnectionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Telling a database that is not there from a query that was wrong.
 *
 * <p>Both reach the assistant as a failed tool call carrying a string, and
 * given the first one unlabelled it writes an apology that reads as its own
 * shortcoming. The user is left unable to tell whether the assistant fell
 * short or their database did.
 *
 * <p>Two directions to get right, and the second is the one with teeth: a
 * missed connection failure costs a confusing paragraph, while a SQL mistake
 * misread as a network failure sends somebody to check their firewall over a
 * mistyped column name.
 */
class AiDatabaseReachabilityTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Connection refused: connect",
            "Communications link failure",
            "The TCP/IP connection to the host 10.0.0.7, port 1433 has failed",
            "Connection is not available, request timed out after 30000ms",
            "java.net.SocketTimeoutException: Read timed out",
            "No route to host",
            "UnknownHostException: db.internal",
            "Login failed for user 'analytics_ro'",
            "FATAL: password authentication failed for user \"reporting\"",
            "server closed the connection unexpectedly",
            "Too many connections",
            "connection.error"})
    void aServerThatCannotBeReachedIsReportedAsExactlyThat(String message) {
        assertTrue(AiDatabaseReachability.isUnreachable(message), message);
        assertNotNull(AiDatabaseReachability.unreachableReport(message), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Invalid column name 'CompanyNmae'",
            "Incorrect syntax near the keyword 'FROM'",
            "Invalid object name 'dbo.Transaction'",
            "ORA-00904: \"AMOUNTT\": invalid identifier",
            "relation \"transactions\" does not exist",
            "Column 'total' in field list is ambiguous",
            "Conversion failed when converting the varchar value 'n/a' to data type int",
            "Arithmetic overflow error converting numeric to data type numeric",
            "datasource.not.found",
            "refused by the sql guard: E_MULTI_STATEMENT"})
    void aServerThatAnsweredAndObjectedIsNotANetworkProblem(String message) {
        // The direction that matters. Every one of these is proof the server is
        // up: it read the statement and disagreed with it.
        assertFalse(AiDatabaseReachability.isUnreachable(message), message);
        assertNull(AiDatabaseReachability.unreachableReport(message), message);
    }

    @Test
    void aConnectionExceptionIsTakenAtItsWord() {
        assertTrue(AiDatabaseReachability.isUnreachable(new ConnectionException("connection.ssh.error")));
    }

    @Test
    void aConnectionSqlStateIsEnoughOnItsOwn() {
        // Class 08 is "connection exception" in the standard, and 28 is
        // credentials that no longer work - which is also something to go and
        // fix rather than something to reword the question about.
        assertTrue(AiDatabaseReachability.isUnreachable(new SQLException("gone", "08S01")));
        assertTrue(AiDatabaseReachability.isUnreachable(new SQLException("gone", "08006")));
        assertTrue(AiDatabaseReachability.isUnreachable(new SQLException("nope", "28000")));
        assertFalse(AiDatabaseReachability.isUnreachable(new SQLException("bad column", "42S22")));
    }

    @Test
    void aStatementErrorWrappedInSomethingVagueStaysAStatementError() {
        // Wrappers add words like "pool" and "i/o error" on the way up, and
        // those are in the connection list. The innermost complaint about the
        // statement has to win, or half the SQL mistakes in the product would
        // be reported as outages.
        BusinessException wrapped = new BusinessException("execution failed",
                new Object[] {}, new SQLException("Invalid column name 'x'", "42S22"));
        assertFalse(AiDatabaseReachability.isUnreachable(wrapped));
    }

    @Test
    void aConnectionFailureWrappedInABusinessExceptionIsStillOne() {
        BusinessException wrapped = new BusinessException("connection.error",
                new Object[] {}, new SQLException("Connection refused: connect", "08S01"));
        assertTrue(AiDatabaseReachability.isUnreachable(wrapped));
    }

    @Test
    void nothingAtAllIsNotAConnectionFailure() {
        assertFalse(AiDatabaseReachability.isUnreachable((Throwable) null));
        assertFalse(AiDatabaseReachability.isUnreachable((String) null));
        assertFalse(AiDatabaseReachability.isUnreachable(""));
        assertNull(AiDatabaseReachability.unreachableReport(new IllegalStateException("something odd")));
    }

    @Test
    void aSelfReferencingCauseChainDoesNotHang() {
        SQLException looping = new SQLException("odd", "HY000") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(AiDatabaseReachability.isUnreachable(looping));
    }

    @Test
    void theReportForbidsEveryEvasionTheModelReachedForBefore() {
        String report = AiDatabaseReachability.unreachableReport("Connection refused: connect");

        assertTrue(report.startsWith(AiDatabaseReachability.MARKER), report);
        assertTrue(report.contains("Connection refused"), report);
        // Each of these is a sentence from the answer that started this.
        assertTrue(report.contains("not a limit of your abilities"), report);
        assertTrue(report.contains("Do not apologise"), report);
        assertTrue(report.contains("do not offer to write the query anyway"), report);
        assertTrue(report.contains("do not ask them to paste the schema"), report);
        // And it has to say what to do instead, in the user's language.
        assertTrue(report.contains("in their own language"), report);
    }

    @Test
    void theDriversOwnWordsSurviveIntoTheReport() {
        String report = AiDatabaseReachability.unreachableReport(
                new SQLException("The TCP/IP connection to the host 10.0.0.7, port 1433 has failed", "08S01"));

        assertTrue(report.contains("port 1433"), report);
    }

    @Test
    void aRamblingDriverMessageIsTrimmedToItsFirstLine() {
        String noisy = "Connection refused: connect\n\tat java.base/sun.nio...\n\tat com.microsoft...";
        assertFalse(AiDatabaseReachability.describe(new SQLException(noisy, "08S01")).contains("\n"));
    }
}
