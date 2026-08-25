package ai.chat2db.spi.util;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two judgements inside the read-only probe.
 *
 * <p>The JDBC round trip is not something a unit test can have, so what is
 * pinned here is everything around it: which engines are touched at all, what
 * is executed when they are, and - the one that matters - when a refusal
 * counts as proof the account cannot write.
 */
class ReadOnlyProbeTest {

    @ParameterizedTest
    @ValueSource(strings = {"POSTGRESQL", "MYSQL", "SQLSERVER", "postgresql", " SqlServer "})
    void theThreeEnginesWhereATempTableCleansItselfUpAreProbed(String dbType) {
        assertTrue(ReadOnlyProbe.isProbeable(dbType), dbType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORACLE", "SQLITE", "MONGODB", "REDIS", "H2", "DB2", "CLICKHOUSE", ""})
    void everywhereElseIsLeftAloneEntirely(String dbType) {
        // Oracle above all: a global temporary table there is real DDL that
        // commits implicitly and leaves a permanent object behind. Creating one
        // in somebody's production schema to find out whether that is allowed
        // is worse than not knowing.
        assertFalse(ReadOnlyProbe.isProbeable(dbType), dbType);
        assertNull(ReadOnlyProbe.createStatementFor(dbType), dbType);
    }

    @Test
    void theSqlServerProbeIsLocalToTheSessionAndNotInTheUsersDatabase() {
        // The leading # is the whole safety property: it puts the table in
        // tempdb, private to this connection, gone when it closes.
        assertTrue(ReadOnlyProbe.createStatementFor("SQLSERVER").contains("#" + ReadOnlyProbe.PROBE_TABLE));
    }

    @Test
    void theOtherTwoUseATemporaryTable() {
        assertTrue(ReadOnlyProbe.createStatementFor("POSTGRESQL").startsWith("CREATE TEMPORARY TABLE"));
        assertTrue(ReadOnlyProbe.createStatementFor("MYSQL").startsWith("CREATE TEMPORARY TABLE"));
    }

    @Test
    void whatIsCreatedCanBeDroppedAgain() {
        for (String dbType : new String[] {"POSTGRESQL", "MYSQL", "SQLSERVER"}) {
            assertTrue(ReadOnlyProbe.dropStatementFor(dbType).contains(ReadOnlyProbe.PROBE_TABLE), dbType);
        }
    }

    @Test
    void aPrivilegeSqlStateIsProofTheAccountCannotWrite() {
        assertTrue(ReadOnlyProbe.refusedForPrivilege(new SQLException("nope", "42501")));
        assertTrue(ReadOnlyProbe.refusedForPrivilege(new SQLException("nope", "28000")));
        assertTrue(ReadOnlyProbe.refusedForPrivilege(new SQLException("nope", "42000")));
    }

    @Test
    void soIsAMessageThatSaysSoUnderAGenericState() {
        // Drivers vary; several report a refusal under a state that says
        // nothing, with the reason only in the text.
        for (String message : new String[] {
                "ERROR: permission denied for schema public",
                "CREATE command denied to user 'analytics_ro'@'10.0.0.4'",
                "CREATE TABLE permission denied in database 'Topia'",
                "Access denied for user 'ro'",
                "cannot execute CREATE TABLE in a read-only transaction"}) {
            assertTrue(ReadOnlyProbe.refusedForPrivilege(new SQLException(message, "HY000")), message);
        }
    }

    @Test
    void anOrdinaryFailureIsNotProofOfAnything() {
        // This is the direction that matters. A timeout, a full disk or a
        // syntax the driver disliked says nothing about privileges, and
        // reading it as "read-only confirmed" would put a green tick on an
        // account that can drop tables.
        for (SQLException failure : new SQLException[] {
                new SQLException("connection timed out", "08S01"),
                new SQLException("could not extend file: No space left on device", "53100"),
                new SQLException("syntax error at or near \"TEMPORARY\"", "42601"),
                new SQLException("deadlock detected", "40P01"),
                new SQLException(),
                null}) {
            assertFalse(ReadOnlyProbe.refusedForPrivilege(failure), String.valueOf(failure));
        }
    }

    @Test
    void aPrivilegeErrorWrappedInSomethingElseStillCounts() {
        SQLException underlying = new SQLException("permission denied for relation", "42501");
        assertTrue(ReadOnlyProbe.refusedForPrivilege(new SQLException("wrapped", "HY000", underlying)));
    }

    @Test
    void aSelfReferencingCauseChainDoesNotHangTheProbe() {
        // Some drivers set a cause to the exception itself; walking that
        // naively never returns, and a connection test that never returns is
        // worse than one that says nothing.
        SQLException looping = new SQLException("odd", "HY000") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertFalse(ReadOnlyProbe.refusedForPrivilege(looping));
    }

    @Test
    void aNullConnectionIsNotVerifiedRatherThanAnException() {
        assertEquals(ReadOnlyProbe.Verdict.NOT_VERIFIED, ReadOnlyProbe.probe(null, "POSTGRESQL"));
    }

    @Test
    void theProbeIsOnUnlessAnOperatorTurnsItOff() {
        // Nothing in the environment here, so this pins the default.
        assertTrue(ReadOnlyProbe.enabled());
    }
}
