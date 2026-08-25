package ai.chat2db.community.domain.core.sqlguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.feature.Feature;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.commons.lang3.StringUtils;

/**
 * The gate every statement the assistant writes has to pass.
 *
 * <p>The model proposes; this disposes. Before this existed the only question
 * asked of a generated statement was what <em>kind</em> of statement it was:
 * classify it, and run it if the answer was SELECT. That is a thin gate in
 * three ways, and each is closed here.
 *
 * <p><b>It trusted the classifier.</b> When the type parser threw, the
 * fallback read the leading keyword and nothing else, so
 * {@code SELECT 1; DROP TABLE x} classified as a SELECT - and the executor
 * forwards the whole string to the driver when parsing does not yield exactly
 * one statement. Here, more than one statement is a refusal with no
 * conditions attached to it.
 *
 * <p><b>A SELECT is not automatically harmless.</b>
 * {@code SELECT * FROM sys.databases} is a SELECT.
 * {@code SELECT * FROM OPENROWSET(...)} is a SELECT. Reading the server's
 * catalog, or reaching through it to another host, is not the thing anyone
 * granted the assistant permission to do.
 *
 * <p><b>It was a blacklist in shape.</b> Anything not recognised as dangerous
 * ran. This is an allowlist: a function nobody listed is refused, so the
 * failure mode of a name we did not think of is a refusal rather than a
 * bypass. An operator who needs one adds it through
 * {@code CHAT2DB_AI_SQL_EXTRA_FUNCTIONS} - deliberately an operator's decision
 * and not the model's.
 *
 * <p>What is <em>not</em> here yet, stated so nobody assumes otherwise: the
 * statement's tables are collected but not checked against a list of tables
 * this connection is allowed to read. That needs a stored schema snapshot,
 * which this product does not keep - it introspects live - so it is a piece of
 * work rather than a line of code. Until then a statement may read any
 * business table on the connection, which is what the connection's own
 * database account permits anyway.
 *
 * <p>Pure by construction: no Spring, no context, no connection. That is what
 * lets the hostile corpus beside it run as an ordinary unit test.
 */
public final class AiSqlGuard {

    /**
     * Text that is refused before anything is parsed.
     *
     * <p>A secondary defence, and second on purpose: a string blacklist is
     * exactly the thing the allowlist below exists to replace. It earns its
     * place by catching constructs that parse as something innocuous - a
     * bulk-load clause, a linked-server call - and by failing fast on a
     * statement that should never have been generated at all.
     */
    private static final List<String> FORBIDDEN_TEXT = List.of(
            // SQL Server: shell, dynamic SQL, and reaching another host
            "xp_cmdshell", "sp_executesql", "sp_oacreate", "openrowset", "opendatasource",
            "openquery", "openxml", "bulk insert", "waitfor delay", "waitfor time",
            // MySQL: file read/write and a timing DoS
            "into outfile", "into dumpfile", "load_file", "benchmark(",
            // PostgreSQL: file access, settings, and killing sessions
            "pg_read_file", "pg_read_binary_file", "pg_ls_dir", "pg_sleep", "pg_stat_file",
            "lo_import", "lo_export", "dblink", "current_setting", "set_config",
            "pg_terminate_backend", "pg_cancel_backend",
            // Oracle: file, network and job packages
            "utl_file", "utl_http", "utl_tcp", "utl_smtp", "utl_inaddr",
            "dbms_scheduler", "dbms_java", "dbms_lob", "dbms_xmlgen");

    /**
     * Schema and object names that are the server talking about itself.
     *
     * <p>Matched on the qualified name and on the bare one, because
     * {@code sys.databases} and a statement already scoped into {@code sys}
     * are the same read.
     */
    private static final List<String> FORBIDDEN_SCHEMAS = List.of(
            "information_schema", "pg_catalog", "pg_toast", "sys", "mysql",
            "performance_schema", "msdb", "sysibm", "sysibmadm", "sysadmin");

    private static final List<String> FORBIDDEN_NAME_PREFIXES = List.of(
            "pg_", "sqlite_", "sysobjects", "syscolumns", "sysusers", "syslogins",
            "sysdatabases", "sysprocesses", "v$", "gv$");

    /**
     * Functions a statement may call.
     *
     * <p>Broad on purpose, and across all four engines, because the cost of
     * being wrong here lands on an ordinary analytical question rather than on
     * an attack: a legitimate {@code STRING_AGG} refused is a product that
     * looks broken. Every name is standard, reads data or transforms a value,
     * and touches neither the filesystem, the network, nor the server's own
     * state.
     */
    private static final Set<String> ALLOWED_FUNCTIONS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    static {
        ALLOWED_FUNCTIONS.addAll(List.of(
                // aggregates
                "count", "sum", "avg", "min", "max", "stddev", "stddev_pop", "stddev_samp",
                "variance", "var_pop", "var_samp", "median", "percentile_cont", "percentile_disc",
                "group_concat", "string_agg", "listagg", "array_agg", "count_big",
                // window
                "row_number", "rank", "dense_rank", "ntile", "lag", "lead",
                "first_value", "last_value", "nth_value", "cume_dist", "percent_rank",
                // numbers
                "abs", "ceil", "ceiling", "floor", "round", "trunc", "truncate", "sign",
                "mod", "power", "pow", "sqrt", "cbrt", "exp", "ln", "log", "log10", "log2",
                "greatest", "least", "rand", "random",
                // text
                "length", "len", "char_length", "character_length", "lower", "upper",
                "initcap", "trim", "ltrim", "rtrim", "lpad", "rpad", "substr", "substring",
                "left", "right", "replace", "reverse", "concat", "concat_ws", "split_part",
                "position", "charindex", "instr", "strpos", "regexp_replace", "regexp_substr",
                "regexp_like", "repeat", "replicate", "space", "ascii", "chr", "char",
                "format", "to_char", "quotename", "translate", "soundex", "difference",
                // dates
                "now", "current_date", "current_time", "current_timestamp", "getdate",
                "getutcdate", "sysdate", "systimestamp", "localtimestamp", "curdate", "curtime",
                "date", "time", "year", "month", "day", "hour", "minute", "second",
                "quarter", "week", "weekday", "dayofweek", "dayofmonth", "dayofyear",
                "datepart", "datename", "dateadd", "datediff", "datefromparts", "eomonth",
                "date_add", "date_sub", "date_trunc", "date_part", "date_format", "datetrunc",
                "extract", "add_months", "months_between", "last_day", "next_day",
                "timestampadd", "timestampdiff", "to_date", "to_timestamp", "from_unixtime",
                "unix_timestamp", "age", "make_date", "convert", "try_convert",
                // conditionals and casts
                "coalesce", "nullif", "ifnull", "isnull", "nvl", "nvl2", "iif", "if",
                "decode", "cast", "try_cast", "to_number", "to_num",
                // sets used inside analytics
                "row_count", "distinct"));
        ALLOWED_FUNCTIONS.addAll(extraFunctions());
    }

    private AiSqlGuard() {
    }

    /**
     * Whether one statement may be executed, and why not when it may not.
     *
     * <p>Fail-closed at every branch, including the ones that are the guard's
     * own fault: a statement this cannot parse is refused, because a statement
     * nobody could read is not one anybody should run.
     *
     * @param sql the statement as the assistant wrote it.
     * @return the verdict; never null.
     */
    public static SqlGuardVerdict inspect(String sql) {
        if (StringUtils.isBlank(sql)) {
            return SqlGuardVerdict.reject("E_EMPTY", "The statement is empty.",
                    "Write one SELECT statement.");
        }

        SqlGuardVerdict textIssue = screenRawText(sql);
        if (textIssue != null) {
            return textIssue;
        }

        Statements parsed;
        try {
            parsed = parseEitherDialect(sql);
        } catch (Exception e) {
            return SqlGuardVerdict.reject("E_PARSE",
                    "The statement could not be parsed: " + firstLine(e.getMessage()),
                    "Emit one syntactically valid SELECT statement.");
        }

        List<Statement> statements = parsed == null || parsed.getStatements() == null
                ? List.of()
                : parsed.getStatements().stream().filter(java.util.Objects::nonNull).toList();

        if (statements.size() != 1) {
            return SqlGuardVerdict.reject("E_MULTI_STATEMENT",
                    "Expected exactly one statement, found " + statements.size() + ".",
                    "Do not chain statements with semicolons. Run one query, read its result, "
                            + "then run the next.");
        }

        Statement statement = statements.get(0);
        if (!(statement instanceof Select)) {
            return SqlGuardVerdict.reject("E_NOT_A_SELECT",
                    "Only SELECT may be executed automatically; this is "
                            + statement.getClass().getSimpleName() + ".",
                    "Read data, never modify it. Show the statement to the user instead of running it.");
        }

        Walk walk = new Walk();
        try {
            walk.getTables(statement);
        } catch (Exception e) {
            return SqlGuardVerdict.reject("E_PARSE",
                    "The statement parsed but could not be inspected: " + firstLine(e.getMessage()),
                    "Simplify it to a plain SELECT ... FROM ... WHERE ... GROUP BY.");
        }

        List<SqlGuardVerdict.Issue> issues = new ArrayList<>();
        for (String table : walk.tables) {
            String systemObject = systemObjectIn(table);
            if (systemObject != null) {
                issues.add(new SqlGuardVerdict.Issue("E_SYSTEM_OBJECT",
                        "The server's own catalog may not be queried: " + systemObject + ".",
                        "Query the business tables on this connection. To describe the database "
                                + "itself, call the schema tools instead of reading the catalog."));
            }
        }
        for (String function : walk.functions) {
            if (!ALLOWED_FUNCTIONS.contains(function)) {
                issues.add(new SqlGuardVerdict.Issue("E_FUNCTION_NOT_ALLOWED",
                        "Function " + function + "() is not on this deployment's allowlist.",
                        "Use standard aggregate, string and date functions. If this one is needed "
                                + "here, an operator can add it to CHAT2DB_AI_SQL_EXTRA_FUNCTIONS."));
            }
        }

        return issues.isEmpty() ? SqlGuardVerdict.allow(List.copyOf(walk.tables))
                : SqlGuardVerdict.reject(issues);
    }

    /**
     * The statement, read the way whichever engine wrote it means it.
     *
     * <p>Square brackets are how SQL Server quotes an identifier, and the
     * parser does not accept them unless asked. That is not a detail: this
     * deployment's own database is SQL Server, and
     * {@code SELECT [CompanyName] FROM [dbo].[TurnoverRanks]} is what a model
     * writes for it. Left alone, the guard would have refused ordinary
     * questions as unparseable and looked like an outage.
     *
     * <p>Plain first, brackets only as a retry, because the two readings
     * disagree elsewhere: a bracket is also array subscripting in PostgreSQL.
     * Trying the standard reading first means the fallback is reached only by
     * a statement no standard reading accepts.
     *
     * @throws Exception when neither reading parses - and then the guard
     *                   refuses, because a statement nobody can read is not
     *                   one anybody should run.
     */
    private static Statements parseEitherDialect(String sql) throws Exception {
        try {
            return CCJSqlParserUtil.parseStatements(sql);
        } catch (Exception plainReadingFailed) {
            try {
                return CCJSqlParserUtil.parseStatements(sql, parser ->
                        parser.getConfiguration().setValue(Feature.allowSquareBracketQuotation, true));
            } catch (Exception bracketReadingFailed) {
                // Report the standard reading's complaint: it is the one an
                // author can act on, and the bracket attempt was ours.
                throw plainReadingFailed;
            }
        }
    }

    private static SqlGuardVerdict screenRawText(String sql) {
        String lowered = sql.toLowerCase(Locale.ROOT);
        for (String needle : FORBIDDEN_TEXT) {
            if (lowered.contains(needle)) {
                return SqlGuardVerdict.reject("E_FORBIDDEN_CONSTRUCT",
                        "The statement contains a construct that is never permitted: '"
                                + needle.trim() + "'.",
                        "Query only the business tables on this connection.");
            }
        }
        // A comment in a generated statement has no author and no reader. It is
        // also how a payload rides along behind something that parses cleanly,
        // so the cheapest answer is that generated SQL carries none.
        if (sql.contains("--") || sql.contains("/*") || sql.contains("#")) {
            return SqlGuardVerdict.reject("E_COMMENT_NOT_ALLOWED",
                    "Comments are not permitted in generated SQL.",
                    "Return the statement without comments.");
        }
        return null;
    }

    /**
     * The part of a table reference that names a system object, or null.
     *
     * <p>Both halves are checked: a qualified {@code sys.databases} and a bare
     * {@code sysobjects} reached through an already-scoped connection are the
     * same read of the same thing.
     */
    static String systemObjectIn(String tableReference) {
        String cleaned = tableReference.replace("\"", "").replace("`", "")
                .replace("[", "").replace("]", "")
                .toLowerCase(Locale.ROOT).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String[] parts = cleaned.split("\\.");
        for (String part : parts) {
            if (FORBIDDEN_SCHEMAS.contains(part)) {
                return part;
            }
            for (String prefix : FORBIDDEN_NAME_PREFIXES) {
                if (part.startsWith(prefix)) {
                    return part;
                }
            }
        }
        return null;
    }

    /**
     * Extra function names an operator has vouched for on this deployment.
     *
     * <p>An escape hatch that belongs to whoever runs the server, never to the
     * model: the model cannot set an environment variable.
     */
    private static Set<String> extraFunctions() {
        String configured = System.getenv("CHAT2DB_AI_SQL_EXTRA_FUNCTIONS");
        if (StringUtils.isBlank(configured)) {
            return Set.of();
        }
        return Arrays.stream(configured.split("[,\\s]+"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private static String firstLine(String message) {
        if (StringUtils.isBlank(message)) {
            return "unreadable";
        }
        String line = message.strip().split("\\R", 2)[0];
        return line.length() > 160 ? line.substring(0, 157) + "..." : line;
    }

    /**
     * One pass over the whole statement, collecting what has to be checked.
     *
     * <p>{@code TablesNamesFinder} already walks every clause, every subquery
     * and every expression to find table names; overriding one visit method
     * borrows that traversal rather than writing a second, less complete one
     * beside it.
     */
    private static final class Walk extends TablesNamesFinder {

        private final Set<String> tables = new LinkedHashSet<>();
        private final Set<String> functions = new LinkedHashSet<>();

        @Override
        public void visit(Function function) {
            if (function != null && StringUtils.isNotBlank(function.getName())) {
                functions.add(function.getName().trim());
            }
            super.visit(function);
        }

        @Override
        public Set<String> getTables(Statement statement) {
            Set<String> found = super.getTables(statement);
            if (found != null) {
                tables.addAll(found);
            }
            return found;
        }
    }
}
