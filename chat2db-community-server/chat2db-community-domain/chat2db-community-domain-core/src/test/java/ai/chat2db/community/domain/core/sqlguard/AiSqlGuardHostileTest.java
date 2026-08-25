package ai.chat2db.community.domain.core.sqlguard;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The hostile corpus. Zero bypasses, or the build fails.
 *
 * <p>Every statement here is one that must never reach a driver. When a
 * construct is added to the guard's allowlist, its abuse case is added here
 * first and watched to fail - a guard tested only against SQL it was written
 * to accept has not been tested.
 *
 * <p>Where a case names an expected rule id, that id is asserted too, because
 * the rejection reason is what the assistant is told and what a log is
 * filtered on. Where the reason is legitimately ambiguous - several rules
 * would each catch it - the case only requires that something did.
 */
class AiSqlGuardHostileTest {

    static Stream<Arguments> hostile() {
        return Stream.of(
                // ── chaining: the fallback classifier read only the first keyword ──
                Arguments.of("SELECT 1; DROP TABLE orders", "E_MULTI_STATEMENT"),
                Arguments.of("SELECT * FROM orders; DELETE FROM orders", "E_MULTI_STATEMENT"),
                Arguments.of("SELECT 1;SELECT 2", "E_MULTI_STATEMENT"),
                Arguments.of("SELECT * FROM orders;\n\nUPDATE orders SET total = 0", "E_MULTI_STATEMENT"),

                // ── writes and DDL ────────────────────────────────────────
                Arguments.of("DROP TABLE orders", "E_NOT_A_SELECT"),
                Arguments.of("DELETE FROM orders WHERE 1=1", "E_NOT_A_SELECT"),
                Arguments.of("UPDATE orders SET total_amount = 0", "E_NOT_A_SELECT"),
                Arguments.of("INSERT INTO orders VALUES (1)", "E_NOT_A_SELECT"),
                Arguments.of("TRUNCATE TABLE orders", "E_NOT_A_SELECT"),
                Arguments.of("CREATE TABLE evil (id int)", "E_NOT_A_SELECT"),
                Arguments.of("ALTER TABLE orders DROP COLUMN status", "E_NOT_A_SELECT"),
                // The parser has no grammar for GRANT, so this is refused as
                // unparseable rather than as not-a-SELECT. Both are refusals
                // and the id is not worth pinning: what matters is that a
                // statement nobody could read does not run.
                Arguments.of("GRANT ALL ON orders TO PUBLIC", null),
                Arguments.of("MERGE INTO orders USING staging ON (1=1) WHEN MATCHED THEN "
                        + "UPDATE SET total_amount = 0", "E_NOT_A_SELECT"),

                // ── the server's own catalog ──────────────────────────────
                Arguments.of("SELECT * FROM sys.databases", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT name FROM master.sys.sql_logins", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM sysobjects", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM information_schema.tables", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM pg_catalog.pg_user", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM mysql.user", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM performance_schema.threads", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM v$session", "E_SYSTEM_OBJECT"),
                // Reached from a subquery rather than the FROM clause.
                Arguments.of("SELECT id FROM orders WHERE id IN (SELECT object_id FROM sys.objects)",
                        "E_SYSTEM_OBJECT"),
                // Quoted, to get past a check that only compared bare text.
                Arguments.of("SELECT * FROM [sys].[databases]", "E_SYSTEM_OBJECT"),
                Arguments.of("SELECT * FROM \"information_schema\".\"tables\"", "E_SYSTEM_OBJECT"),

                // ── shell, files, and reaching another host ───────────────
                Arguments.of("SELECT * FROM OPENROWSET('SQLNCLI', 'server=evil;', 'SELECT 1')", null),
                Arguments.of("SELECT * FROM OPENQUERY(linked, 'SELECT 1')", null),
                Arguments.of("EXEC xp_cmdshell 'dir'", null),
                Arguments.of("SELECT pg_read_file('/etc/passwd')", null),
                Arguments.of("SELECT lo_import('/etc/passwd')", null),
                Arguments.of("SELECT * FROM dblink('host=evil', 'SELECT 1') AS t(x int)", null),
                Arguments.of("SELECT load_file('/etc/passwd')", null),
                Arguments.of("SELECT * FROM orders INTO OUTFILE '/tmp/x'", null),
                Arguments.of("SELECT utl_http.request('http://evil/') FROM dual", null),

                // ── denial of service by waiting ──────────────────────────
                Arguments.of("SELECT pg_sleep(600)", null),
                Arguments.of("SELECT benchmark(100000000, md5('x'))", null),
                Arguments.of("WAITFOR DELAY '00:10:00'", null),

                // ── reading or changing the server's own state ────────────
                Arguments.of("SELECT current_setting('is_superuser')", null),
                Arguments.of("SELECT set_config('log_statement', 'none', false)", null),
                Arguments.of("SELECT pg_terminate_backend(1234)", null),

                // ── comments, which is how a payload rides along ──────────
                Arguments.of("SELECT id FROM orders -- ; DROP TABLE orders", "E_COMMENT_NOT_ALLOWED"),
                Arguments.of("SELECT /* nothing to see */ id FROM orders", "E_COMMENT_NOT_ALLOWED"),

                // ── a function nobody listed ──────────────────────────────
                Arguments.of("SELECT some_undeclared_udf(id) FROM orders", "E_FUNCTION_NOT_ALLOWED"),
                // Buried in a subquery, past a check that only read the top level.
                Arguments.of("SELECT id FROM orders WHERE id > (SELECT MAX(some_undeclared_udf(id)) "
                        + "FROM orders)", "E_FUNCTION_NOT_ALLOWED"),

                // ── nothing to run ────────────────────────────────────────
                Arguments.of("", "E_EMPTY"),
                Arguments.of("   ", "E_EMPTY"),
                Arguments.of("this is not sql at all", "E_PARSE"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hostile")
    void isRefused(String sql, String expectedRuleId) {
        SqlGuardVerdict verdict = AiSqlGuard.inspect(sql);

        assertFalse(verdict.allowed(), "the guard let this through: " + sql);
        if (expectedRuleId != null) {
            assertEquals(expectedRuleId, verdict.issues().get(0).ruleId(),
                    "refused for the wrong reason: " + sql + " -> " + verdict.ruleIds());
        }
    }
}
