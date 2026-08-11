package ai.chat2db.community.tools.util;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Coverage for the deadline put on statements the assistant runs.
 *
 * <p>Two things have to hold. A statement executed inside the scope must carry
 * the deadline - a query nobody is watching must not be able to walk a table
 * with a billion rows in it unchecked. And the deadline must never outlive its
 * scope: these run on pooled request threads, so one leaked value would apply
 * itself to whatever unrelated work the thread picks up next.
 */
class SqlExecutionLimitsTest {

    /** Records what was asked of it; everything else throws if touched. */
    private static class RecordingStatement implements Statement {
        private Integer appliedTimeout;

        @Override
        public void setQueryTimeout(int seconds) throws SQLException {
            appliedTimeout = seconds;
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            throw new UnsupportedOperationException();
        }

        // The remaining Statement methods are never called by the code under test.
        @Override
        public java.sql.ResultSet executeQuery(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int executeUpdate(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // nothing to release
        }

        @Override
        public int getMaxFieldSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMaxFieldSize(int max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMaxRows() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMaxRows(int max) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEscapeProcessing(boolean enable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getQueryTimeout() {
            return appliedTimeout == null ? 0 : appliedTimeout;
        }

        @Override
        public void cancel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.sql.SQLWarning getWarnings() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearWarnings() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setCursorName(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean execute(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.sql.ResultSet getResultSet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUpdateCount() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getMoreResults() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFetchDirection(int direction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getFetchDirection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setFetchSize(int rows) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getFetchSize() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getResultSetConcurrency() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getResultSetType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addBatch(String sql) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] executeBatch() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.sql.Connection getConnection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getMoreResults(int current) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.sql.ResultSet getGeneratedKeys() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int executeUpdate(String sql, int[] columnIndexes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int executeUpdate(String sql, String[] columnNames) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean execute(String sql, int autoGeneratedKeys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean execute(String sql, int[] columnIndexes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean execute(String sql, String[] columnNames) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getResultSetHoldability() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isClosed() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPoolable(boolean poolable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPoolable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeOnCompletion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCloseOnCompletion() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void appliesTheDeadlineToAStatementInsideTheScope() {
        RecordingStatement statement = new RecordingStatement();

        SqlExecutionLimits.runWithTimeout(300, () -> {
            SqlExecutionLimits.apply(statement);
            return null;
        });

        assertEquals(300, statement.appliedTimeout);
    }

    @Test
    void leavesAStatementOutsideTheScopeAlone() {
        RecordingStatement statement = new RecordingStatement();

        SqlExecutionLimits.apply(statement);

        assertNull(statement.appliedTimeout, "a console query is watched by whoever typed it");
    }

    @Test
    void clearsTheDeadlineWhenTheScopeEnds() {
        SqlExecutionLimits.runWithTimeout(300, () -> null);

        assertNull(SqlExecutionLimits.currentTimeoutSeconds(), "a pooled thread must not inherit a deadline");
    }

    @Test
    void clearsTheDeadlineEvenWhenTheWorkThrows() {
        assertThrows(IllegalStateException.class, () -> SqlExecutionLimits.runWithTimeout(300, () -> {
            throw new IllegalStateException("query failed");
        }));

        assertNull(SqlExecutionLimits.currentTimeoutSeconds(), "a failed query must not leave its deadline behind");
    }

    @Test
    void restoresAnOuterDeadlineRatherThanDroppingIt() {
        SqlExecutionLimits.runWithTimeout(300, () -> {
            SqlExecutionLimits.runWithTimeout(30, () -> null);
            assertEquals(300, SqlExecutionLimits.currentTimeoutSeconds());
            return null;
        });

        assertNull(SqlExecutionLimits.currentTimeoutSeconds());
    }

    @Test
    void treatsANonPositiveOrMissingTimeoutAsNoDeadline() {
        RecordingStatement statement = new RecordingStatement();

        SqlExecutionLimits.runWithTimeout(0, () -> {
            SqlExecutionLimits.apply(statement);
            return null;
        });
        SqlExecutionLimits.runWithTimeout(null, () -> {
            SqlExecutionLimits.apply(statement);
            return null;
        });

        assertNull(statement.appliedTimeout);
    }

    @Test
    void survivesADriverThatRefusesQueryTimeouts() {
        Statement refusing = new RecordingStatement() {
            @Override
            public void setQueryTimeout(int seconds) throws SQLException {
                throw new SQLFeatureNotSupportedException("not supported");
            }
        };

        // The query the user asked for still runs; it simply runs without a deadline.
        assertDoesNotThrow(() -> SqlExecutionLimits.runWithTimeout(300, () -> {
            SqlExecutionLimits.apply(refusing);
            return null;
        }));
    }
}
