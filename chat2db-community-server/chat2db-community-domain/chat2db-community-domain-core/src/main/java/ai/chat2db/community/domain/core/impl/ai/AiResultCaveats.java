package ai.chat2db.community.domain.core.impl.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.lang3.StringUtils;

/**
 * What the assistant has to be told about a result before it writes about it.
 *
 * <p>The third failure mode. Two are already handled: the SQL was refused, or
 * the database refused to run it - both mean there is no result to describe.
 * This covers the expensive one, where the query ran, came back looking
 * perfectly reasonable, and the answer written from it is wrong.
 *
 * <p>A caveat here is <em>structural</em>: it reads the question, the SQL and
 * the result's shape, never a result value. That is what makes it affordable
 * on every single query - no tokens, no model call, no latency - and it is
 * also what makes it trustworthy, because it cannot inherit the misreading
 * that produced the query in the first place.
 *
 * <p>None of this rewrites a query or fails a run. A caveat informs the answer
 * and nothing more. The one that matters most is the row cap: the platform
 * decides how many rows come back, and a model that is handed 200 rows without
 * being told 200 was the limit will describe them as if they were all there
 * were. That is not hypothetical - it is where "the only company at rank 1 is
 * RasanRis" came from, off a page of 200 identical rows with more behind it.
 */
public final class AiResultCaveats {

    /**
     * Questions whose natural answer is one number.
     *
     * <p>Kept narrow in both languages: a false positive costs a line of noise
     * in a result the model still reads correctly, but noise spends attention
     * that the real caveats need. Persian first, because that is what this
     * deployment is asked in.
     */
    private static final Pattern SINGLE_FIGURE = Pattern.compile(
            "چند\\s*تا|چه\\s*تعداد|چقدر|مجموع|جمع\\s*کل|میانگین|تعداد\\s*کل"
                    + "|\\bhow many\\b|\\bhow much\\b|\\btotal number of\\b|\\bcount of\\b"
                    + "|\\bwhat (is|was) the (total|average|sum|number)\\b",
            Pattern.CASE_INSENSITIVE);

    private AiResultCaveats() {
    }

    /**
     * The caveats that apply to one result, most actionable first.
     *
     * <p>Never throws. A question this cannot parse simply has no opinion
     * about it, which is the same posture the chart planner takes: the cost of
     * being wrong here is a missing line, so every branch fails quiet.
     *
     * @param question    what the user asked, when it is known; blank is fine.
     * @param sql         the statement that produced the result.
     * @param rowCount    rows actually returned.
     * @param hitTheCap   true when more rows exist behind the page that came back.
     * @return the caveats, in the order they should be read; never null.
     */
    public static List<String> forResult(String question, String sql, int rowCount, boolean hitTheCap) {
        List<String> caveats = new ArrayList<>(2);

        if (hitTheCap) {
            caveats.add("PARTIAL RESULT: the platform capped this query at " + rowCount
                    + " rows and more rows exist. The true total is unknown from these rows alone, so do not "
                    + "state a total, a count, or any claim about \"all\", \"only\", or \"the top N\" of the "
                    + "full set. To answer that, run a second query that aggregates in SQL - COUNT, SUM, "
                    + "GROUP BY, or SELECT DISTINCT - rather than reading these rows.");
        }

        if (rowCount == 0) {
            caveats.add("The query ran and matched no rows. This is often the correct answer - a filter "
                    + "combination or a date range with no data behind it. Only re-check the query if the "
                    + "question implies matching rows should exist.");
            return caveats;
        }

        if (rowCount > 1 && asksForOneNumber(question) && Boolean.FALSE.equals(groups(sql))) {
            caveats.add("The question asks for a single figure but " + rowCount + " rows came back, and "
                    + "nothing in the query groups them. The multiplicity is an unaggregated SELECT or a "
                    + "join fanning out. Aggregate in SQL rather than counting these rows yourself.");
        }

        return caveats;
    }

    static boolean asksForOneNumber(String question) {
        return StringUtils.isNotBlank(question) && SINGLE_FIGURE.matcher(question).find();
    }

    /**
     * Whether the statement groups its rows.
     *
     * <p>Three-valued on purpose. {@code null} means the SQL could not be
     * read, and the caller treats that as no opinion rather than as "does not
     * group" - a granularity caveat raised because the parser choked would be
     * a caveat about our parser, told to the model as if it were about its
     * query.
     *
     * <p>A bare aggregate over the whole table - {@code SELECT COUNT(*) FROM t}
     * - has no GROUP BY and returns one row, so it never reaches this: the
     * caller has already required more than one row.
     */
    static Boolean groups(String sql) {
        if (StringUtils.isBlank(sql)) {
            return null;
        }
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select select)) {
                return null;
            }
            if (!(select.getSelectBody() instanceof PlainSelect plain)) {
                // A UNION or a set operation: several selects, each with its
                // own grouping. Not worth an opinion.
                return null;
            }
            return plain.getGroupBy() != null;
        } catch (Exception e) {
            // Unparseable here is the guard's problem, not this module's.
            return null;
        }
    }
}
