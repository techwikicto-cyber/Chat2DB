package ai.chat2db.community.domain.api.model.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proposing which view here implements which label in the library.
 *
 * <p>The hit rate matters less than the two refusals. A wrong binding produces
 * a plausible figure from the wrong table, which is the worst failure this
 * product has - so an ambiguous name is left for a person, and a binding
 * somebody already made is never guessed over.
 */
class AiBindingSuggesterTest {

    /** What the team's own ETL produces at every customer. */
    private static final List<String> STANDARD_WAREHOUSE = List.of(
            "dbo.vw_Sales", "dbo.vw_Customer", "dbo.vw_GL", "dbo.vw_Inventory", "dbo.SomethingElse");

    @Test
    void aStandardWarehouseIsRecognisedWithoutBeingTold() {
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("sales", "customer", "gl"), STANDARD_WAREHOUSE, Map.of());

        assertEquals(Map.of(
                        "sales", "dbo.vw_Sales",
                        "customer", "dbo.vw_Customer",
                        "gl", "dbo.vw_GL"),
                AiBindingSuggester.asBindings(suggestions));
    }

    @Test
    void theEtlPrefixesTeamsUseAreSeenThrough() {
        for (String naming : List.of("dbo.vw_Sales", "dbo.V_SALES", "sales", "dbo.tbl_sales", "SALES")) {
            List<AiBindingSuggester.Suggestion> suggestions =
                    AiBindingSuggester.suggest(List.of("sales"), List.of(naming), Map.of());

            assertTrue(suggestions.get(0).confident(), naming);
            assertEquals(naming, suggestions.get(0).suggested(), naming);
        }
    }

    @Test
    void aQualifiedNameIsMatchedOnItsLastSegment() {
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("sales"), List.of("TopiaDB.dbo.vw_Sales"), Map.of());

        assertEquals("TopiaDB.dbo.vw_Sales", suggestions.get(0).suggested());
    }

    @Test
    void twoViewsThatBothLookRightAreLeftForAPerson() {
        // The operator knows something this does not. Choosing for them would
        // hide the decision they need to make, and a wrong binding here is a
        // plausible number from the wrong table.
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("sales"), List.of("dbo.vw_Sales", "archive.vw_Sales"), Map.of());

        assertFalse(suggestions.get(0).confident());
        assertEquals(2, suggestions.get(0).candidates().size());
    }

    @Test
    void aNearMissIsOfferedButNotChosen() {
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("sales"), List.of("dbo.vw_SalesDetail"), Map.of());

        assertFalse(suggestions.get(0).confident(), "close is not the same as right");
        assertEquals(List.of("dbo.vw_SalesDetail"), suggestions.get(0).candidates());
    }

    @Test
    void nothingLikeItAtAllIsReportedAsNothing() {
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("inventory"), List.of("dbo.vw_Sales", "dbo.vw_Customer"), Map.of());

        assertFalse(suggestions.get(0).confident());
        assertTrue(suggestions.get(0).candidates().isEmpty());
        assertTrue(AiBindingSuggester.asBindings(suggestions).isEmpty());
    }

    @Test
    void abindingSomebodyAlreadyMadeIsNeverGuessedOver() {
        // They corrected it for a reason, and re-guessing would quietly undo
        // that on the next visit to the screen.
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("sales"), STANDARD_WAREHOUSE, Map.of("sales", "reporting.vw_SalesRestated"));

        assertEquals("reporting.vw_SalesRestated", suggestions.get(0).suggested());
    }

    @Test
    void aBlankExistingBindingIsTreatedAsUnset() {
        List<AiBindingSuggester.Suggestion> suggestions = AiBindingSuggester.suggest(
                List.of("sales"), STANDARD_WAREHOUSE, Map.of("sales", "  "));

        assertEquals("dbo.vw_Sales", suggestions.get(0).suggested());
    }

    @Test
    void bracketedAndQuotedNamesAreTheSameNames() {
        assertEquals(AiBindingSuggester.normalise("vw_Sales"), AiBindingSuggester.normalise("[vw_Sales]"));
        assertEquals(AiBindingSuggester.normalise("vw_Sales"), AiBindingSuggester.normalise("\"VW_SALES\""));
        assertEquals(AiBindingSuggester.normalise("sales"), AiBindingSuggester.normalise("vw_sales"));
    }

    @Test
    void aPrefixThatIsTheWholeNameIsNotStrippedAway() {
        // "vw" as a table name is odd, but stripping it to nothing would make
        // it match everything.
        assertFalse(AiBindingSuggester.normalise("vw").isEmpty());
    }

    @Test
    void nothingToWorkWithProducesNothingRatherThanThrowing() {
        assertTrue(AiBindingSuggester.suggest(null, STANDARD_WAREHOUSE, Map.of()).isEmpty());
        assertFalse(AiBindingSuggester.suggest(List.of("sales"), null, null).get(0).confident());
        assertFalse(AiBindingSuggester.suggest(List.of("sales"), List.of(), null).get(0).confident());
    }
}
