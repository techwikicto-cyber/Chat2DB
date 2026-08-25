package ai.chat2db.community.domain.core.impl.ai;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the assistant is told about a result before it writes about it.
 *
 * <p>The case that produced this: a question about which companies hold rank 1,
 * a query returning raw rows, and a page of 200 that all named the same
 * company - with more rows behind it. The answer said that company was the only
 * one. Everything needed to know better was already in the result and none of
 * it was phrased as something to act on.
 */
class AiResultCaveatsTest {

    private static final String RANK_ONE =
            "SELECT CompanyName, Rank1 FROM dbo.TurnoverRanks WHERE Rank1 = 1";

    @Test
    void aCappedResultSaysSoAndForbidsTheClaimItWouldOtherwiseInvite() {
        List<String> caveats = AiResultCaveats.forResult(
                "شرکت‌های با رتبه ۱ را بگو", RANK_ONE, 200, true);

        assertEquals(1, caveats.size(), caveats.toString());
        String partial = caveats.get(0);
        assertTrue(partial.contains("200"), partial);
        // The three words an answer written off a capped page reaches for.
        assertTrue(partial.contains("\"all\""), partial);
        assertTrue(partial.contains("\"only\""), partial);
        assertTrue(partial.contains("the top N"), partial);
        // And what to do instead, because a prohibition with no alternative is
        // one the model talks itself out of.
        assertTrue(partial.contains("DISTINCT"), partial);
    }

    @Test
    void acompleteResultCarriesNoCaveatAtAll() {
        // The common case, and it has to stay silent: a caveat on every result
        // is a caveat the model stops reading.
        assertTrue(AiResultCaveats.forResult(
                "شرکت‌های با رتبه ۱ را بگو", RANK_ONE, 12, false).isEmpty());
    }

    @Test
    void oneNumberAskedForAndManyRowsBackIsWorthSaying() {
        List<String> caveats = AiResultCaveats.forResult(
                "چند تا سفارش داریم؟", "SELECT id FROM orders", 431, false);

        assertEquals(1, caveats.size(), caveats.toString());
        assertTrue(caveats.get(0).contains("431"), caveats.toString());
    }

    @Test
    void aGroupedQueryIsShapedRightHoweverManyRowsItReturns() {
        // "How many orders per region?" trips the phrase and is correct.
        assertTrue(AiResultCaveats.forResult(
                "چند تا سفارش در هر استان داریم؟",
                "SELECT region, COUNT(*) FROM orders GROUP BY region",
                31, false).isEmpty());
    }

    @Test
    void anEmptyResultIsReportedAsProbablyCorrect() {
        List<String> caveats = AiResultCaveats.forResult(
                "سفارش‌های دیروز", "SELECT * FROM orders WHERE d = '2020-01-01'", 0, false);

        assertEquals(1, caveats.size(), caveats.toString());
        assertTrue(caveats.get(0).contains("often the correct answer"), caveats.toString());
    }

    @Test
    void aCappedEmptyResultIsImpossibleButStillSaysTheUsefulThing() {
        // Defensive: the executor is the one deciding both flags, and a caveat
        // module that contradicts itself on a shape it did not expect is worse
        // than one that says both things.
        List<String> caveats = AiResultCaveats.forResult("چند تا؟", RANK_ONE, 0, true);

        assertEquals(2, caveats.size(), caveats.toString());
        assertTrue(caveats.get(0).startsWith("PARTIAL RESULT"), caveats.toString());
    }

    @Test
    void bothLanguagesAreRecognisedAsAskingForOneNumber() {
        for (String question : new String[] {
                "چند تا مشتری داریم؟", "چه تعداد سفارش ثبت شده؟", "مجموع فروش امسال",
                "میانگین مبلغ فاکتور", "جمع کل درآمد",
                "how many customers do we have?", "what was the total revenue?",
                "count of orders last month"}) {
            assertTrue(AiResultCaveats.asksForOneNumber(question), question);
        }
    }

    @Test
    void aQuestionThatWantsAListIsNotOneThatWantsANumber() {
        for (String question : new String[] {
                "شرکت‌های با رتبه ۱ را بگو", "لیست مشتریان تهران",
                "which companies are at rank 1?", "show me the orders table"}) {
            assertFalse(AiResultCaveats.asksForOneNumber(question), question);
        }
    }

    @Test
    void noQuestionMeansNoOpinionAboutGranularity() {
        // The web layer may not have put one in the tool context, and a caveat
        // guessed from an absent question would be a caveat about nothing.
        assertTrue(AiResultCaveats.forResult(null, "SELECT id FROM orders", 431, false).isEmpty());
        assertTrue(AiResultCaveats.forResult("  ", "SELECT id FROM orders", 431, false).isEmpty());
    }

    @Test
    void unreadableSqlProducesNoGranularityCaveat() {
        // No opinion, rather than an opinion about our own parser told to the
        // model as if it were about its query.
        assertNull(AiResultCaveats.groups("this is not sql at all"));
        assertTrue(AiResultCaveats.forResult(
                "چند تا؟", "this is not sql at all", 431, false).isEmpty());
    }

    @Test
    void theRowCapIsReportedEvenWhenTheSqlCannotBeRead() {
        // The cap is a fact about the platform, not about the statement, so it
        // survives everything the parser cannot do.
        List<String> caveats = AiResultCaveats.forResult("چیزی", "))) not sql", 200, true);

        assertEquals(1, caveats.size(), caveats.toString());
        assertTrue(caveats.get(0).startsWith("PARTIAL RESULT"), caveats.toString());
    }

    @Test
    void groupingIsDetectedAndItsAbsenceIsToo() {
        assertEquals(Boolean.TRUE, AiResultCaveats.groups(
                "SELECT region, COUNT(*) FROM orders GROUP BY region"));
        assertEquals(Boolean.FALSE, AiResultCaveats.groups("SELECT id FROM orders"));
        // A set operation has one grouping per branch; no single answer.
        assertNull(AiResultCaveats.groups("SELECT id FROM a UNION SELECT id FROM b"));
        // Not a query at all.
        assertNull(AiResultCaveats.groups("UPDATE orders SET x = 1"));
    }
}
