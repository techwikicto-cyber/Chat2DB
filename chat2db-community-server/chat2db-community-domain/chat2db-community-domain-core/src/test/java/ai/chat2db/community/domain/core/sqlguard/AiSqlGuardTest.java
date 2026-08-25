package ai.chat2db.community.domain.core.sqlguard;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The half of the guard that decides whether the product still works.
 *
 * <p>A gate that refuses everything passes the hostile corpus perfectly. What
 * it costs is measured here: these are ordinary analytical statements across
 * the four engines this product connects to, and every one of them has to run.
 * A false rejection lands on a user asking a reasonable question, which is a
 * worse day than the attack the rule was written for.
 */
class AiSqlGuardTest {

    static Stream<String> ordinaryAnalytics() {
        return Stream.of(
                "SELECT CompanyName, Rank1 FROM dbo.TurnoverRanks WHERE Rank1 = 1",
                "SELECT DISTINCT CompanyName FROM dbo.TurnoverRanks WHERE Rank1 = 1 ORDER BY CompanyName",
                "SELECT region, COUNT(*) AS orders, SUM(total_amount) AS revenue "
                        + "FROM orders GROUP BY region HAVING COUNT(*) > 10 ORDER BY revenue DESC",
                "SELECT o.id, c.name FROM orders o JOIN customers c ON c.id = o.customer_id "
                        + "LEFT JOIN regions r ON r.id = c.region_id WHERE o.status <> 'void'",
                "WITH monthly AS (SELECT DATE_TRUNC('month', order_date) AS m, SUM(total_amount) AS rev "
                        + "FROM orders GROUP BY 1) SELECT m, rev, LAG(rev) OVER (ORDER BY m) FROM monthly",
                "SELECT name, ROW_NUMBER() OVER (PARTITION BY region ORDER BY revenue DESC) AS rn "
                        + "FROM sales_by_customer",
                "SELECT COUNT(*) FROM orders WHERE order_date >= CURRENT_DATE - INTERVAL '3 months'",
                "SELECT id FROM orders UNION SELECT id FROM archived_orders",
                "SELECT COALESCE(NULLIF(TRIM(name), ''), 'unknown') FROM customers",
                "SELECT CAST(total_amount AS DECIMAL(10,2)) FROM orders",
                "SELECT CASE WHEN total_amount > 100 THEN 'big' ELSE 'small' END AS bucket, "
                        + "COUNT(*) FROM orders GROUP BY 1",
                "SELECT * FROM orders WHERE id IN (SELECT order_id FROM order_items WHERE quantity > 5)",
                // SQL Server idiom
                "SELECT TOP 10 CompanyName, FORMAT(SumTransactions, 'N0') FROM dbo.TurnoverRanks "
                        + "ORDER BY SumTransactions DESC",
                "SELECT DATEDIFF(day, AccountRegistrationDate, DateProccessing) FROM dbo.TurnoverRanks",
                "SELECT STRING_AGG(CompanyName, ', ') FROM dbo.TurnoverRanks WHERE Rank1 = 1",
                "SELECT ISNULL(Cluster, 0) FROM dbo.TurnoverRanks",
                // Bracket-quoted identifiers: how SQL Server writes, and what
                // this deployment's own database is. The parser does not accept
                // them unless asked, so without the retry the guard refused
                // ordinary questions as unparseable.
                "SELECT [CompanyName], [Rank1] FROM [dbo].[TurnoverRanks] WHERE [Rank1] = 1",
                "SELECT DISTINCT [CompanyName] FROM [TopiaDB].[dbo].[TurnoverRanks]",
                "SELECT [o].[id] FROM [dbo].[orders] AS [o] "
                        + "JOIN [dbo].[customers] AS [c] ON [c].[id] = [o].[customer_id]",
                // Oracle idiom
                "SELECT NVL(name, 'unknown'), TO_CHAR(created_at, 'YYYY-MM') FROM customers",
                "SELECT LISTAGG(name, ',') WITHIN GROUP (ORDER BY name) FROM customers",
                // MySQL idiom
                "SELECT IFNULL(name, 'unknown'), DATE_FORMAT(created_at, '%Y-%m') FROM customers",
                "SELECT GROUP_CONCAT(name) FROM customers GROUP BY region_id");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("ordinaryAnalytics")
    void isAllowed(String sql) {
        SqlGuardVerdict verdict = AiSqlGuard.inspect(sql);

        assertTrue(verdict.allowed(), "refused ordinary analytics: " + sql + " -> " + verdict.feedback());
    }

    @Test
    void aTrailingSemicolonIsOneStatementNotTwo() {
        // How every model writes SQL, and it must not read as chaining.
        assertTrue(AiSqlGuard.inspect("SELECT id FROM orders;").allowed());
    }

    @Test
    void theTablesItReadAreReportedBack() {
        SqlGuardVerdict verdict = AiSqlGuard.inspect(
                "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id");

        assertTrue(verdict.allowed());
        assertEquals(2, verdict.tables().size(), verdict.tables().toString());
    }

    @Test
    void aRefusalTellsTheAssistantWhatToDoNext() {
        SqlGuardVerdict verdict = AiSqlGuard.inspect("SELECT 1; DROP TABLE orders");

        String feedback = verdict.feedback();
        assertTrue(feedback.contains("E_MULTI_STATEMENT"), feedback);
        // A refusal with no way forward is one the model retries verbatim.
        assertTrue(feedback.contains("Return one corrected SELECT statement"), feedback);
        assertEquals("E_MULTI_STATEMENT", verdict.ruleIds());
    }

    @Test
    void anAllowedStatementHasNothingToSay() {
        assertEquals("", AiSqlGuard.inspect("SELECT id FROM orders").feedback());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "orders", "dbo.orders", "public.customers", "system_settings", "sales.systems",
            "user_accounts", "all_orders", "dba_requests", "syndication", "pg2_reports"})
    void ordinaryTableNamesAreNotMistakenForTheCatalog(String table) {
        // The prefix list is narrow on purpose: `system_settings` and
        // `user_accounts` are business tables, and refusing them to be safe
        // about `sysobjects` would be the wrong trade. `syndication` starts
        // with "sy" and `pg2_reports` with "pg" - neither is a catalog name.
        assertNull(AiSqlGuard.systemObjectIn(table), table);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sys.databases", "master.sys.objects", "information_schema.columns",
            "pg_catalog.pg_class", "pg_stat_activity", "mysql.user", "sqlite_master",
            "v$session", "gv$sql", "SYSOBJECTS", "[sys].[databases]"})
    void catalogNamesAreCaughtQualifiedOrNot(String table) {
        assertFalse(AiSqlGuard.systemObjectIn(table) == null, table);
    }
}
