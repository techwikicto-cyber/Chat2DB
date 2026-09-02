package ai.chat2db.community.domain.api.model.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Putting a portable definition together with one customer's tables.
 *
 * <p>The behaviour worth pinning is the refusal. A metric whose label nothing
 * is bound to must resolve to nothing usable - because the alternative is
 * handing the model a definition with a hole in it and letting it improvise the
 * missing table, and a figure computed from an improvised table looks exactly
 * like a real one.
 */
class AiMetricResolverTest {

    private static final Map<String, String> BINDINGS = Map.of(
            "sales", "dbo.vw_Sales",
            "customer", "dbo.vw_Customer");

    @Test
    void aBoundMetricBecomesSqlThatRunsHere() {
        AiMetricResolver.Resolved resolved = AiMetricResolver.resolve(monthlySales(), BINDINGS);

        assertTrue(resolved.usable());
        assertEquals("SUM(dbo.vw_Sales.NetAmount)", resolved.getSql());
        assertEquals("dbo.vw_Sales.DocType = 'INV' AND dbo.vw_Sales.IsVoid = 0", resolved.getFilter());
        assertEquals("dbo.vw_Sales.DocDate", resolved.getTimeColumn());
    }

    @Test
    void aMetricWithNothingBoundToItsSourceIsNotUsable() {
        AiMetricResolver.Resolved resolved = AiMetricResolver.resolve(monthlySales(), Map.of());

        assertFalse(resolved.usable());
        assertEquals(List.of("sales"), resolved.getMissingSources());
    }

    @Test
    void aHalfBoundMetricIsAlsoRefused() {
        // Two labels, one bound. Substituting the one that exists and leaving
        // the other would produce SQL that looks finished and is not.
        AiMetric perCustomer = monthlySales();
        perCustomer.setRequires(List.of("sales", "customer"));
        perCustomer.setSql("SUM({sales}.NetAmount) / COUNT(DISTINCT {customer}.Id)");

        AiMetricResolver.Resolved resolved =
                AiMetricResolver.resolve(perCustomer, Map.of("sales", "dbo.vw_Sales"));

        assertFalse(resolved.usable());
        assertEquals(List.of("customer"), resolved.getMissingSources());
    }

    @Test
    void aBlankBindingCountsAsNoBinding() {
        // An operator who cleared the field meant to clear it.
        AiMetricResolver.Resolved resolved =
                AiMetricResolver.resolve(monthlySales(), Map.of("sales", "   "));

        assertFalse(resolved.usable());
        assertEquals(List.of("sales"), resolved.getMissingSources());
    }

    @Test
    void aLabelDeclaredButNeverWrittenIntoTheSqlIsStillRequired() {
        // `requires` is the promise the connection has to keep; the operator
        // should see it unbound in the same list as everything else.
        AiMetric metric = monthlySales();
        metric.setRequires(List.of("sales", "exchange_rate"));

        AiMetricResolver.Resolved resolved = AiMetricResolver.resolve(metric, BINDINGS);

        assertFalse(resolved.usable());
        assertEquals(List.of("exchange_rate"), resolved.getMissingSources());
    }

    @Test
    void onlyTheMetricsThisConnectionCanComputeAreOffered() {
        AiMetric unbound = new AiMetric();
        unbound.setId("stock_turn");
        unbound.setName("گردش انبار");
        unbound.setRequires(List.of("inventory"));
        unbound.setSql("SUM({inventory}.Qty)");

        AiConceptLibrary library = new AiConceptLibrary();
        library.setMetrics(List.of(monthlySales(), unbound));

        List<AiMetricResolver.Resolved> usable = AiMetricResolver.resolveUsable(library, BINDINGS);

        assertEquals(1, usable.size(), usable.toString());
        assertEquals("monthly_sales", usable.get(0).getMetricId());
    }

    @Test
    void aRetiredMetricIsNotOffered() {
        AiMetric retired = monthlySales();
        retired.setEnabled(false);
        AiConceptLibrary library = new AiConceptLibrary();
        library.setMetrics(List.of(retired));

        assertTrue(AiMetricResolver.resolveUsable(library, BINDINGS).isEmpty());
    }

    @Test
    void theLibraryCanSayWhatEveryConnectionHasToProvide() {
        AiMetric second = new AiMetric();
        second.setId("stock_turn");
        second.setRequires(List.of("inventory", "sales"));

        AiConceptLibrary library = new AiConceptLibrary();
        library.setMetrics(List.of(monthlySales(), second));

        // In first-seen order, without repeats: this is the checklist the
        // binding screen is built from.
        assertEquals(List.of("sales", "inventory"), library.requiredSources());
    }

    @Test
    void placeholdersAreFoundWhereverTheyAppear() {
        assertEquals(List.of("sales"),
                AiMetricResolver.placeholdersIn("SUM({sales}.Net) - SUM({sales}.Tax)"));
        assertEquals(List.of("a", "b"), AiMetricResolver.placeholdersIn("{a}.x + {b}.y"));
        assertTrue(AiMetricResolver.placeholdersIn("SUM(NetAmount)").isEmpty());
        assertTrue(AiMetricResolver.placeholdersIn(null).isEmpty());
    }

    @Test
    void aTableNameWithARegexCharacterInItSurvivesSubstitution() {
        // Table names carry $ and \ in more schemas than anyone expects, and an
        // unescaped replacement would corrupt the SQL rather than fail loudly.
        AiMetric metric = monthlySales();
        metric.setSql("SUM({sales}.NetAmount)");

        AiMetricResolver.Resolved resolved =
                AiMetricResolver.resolve(metric, Map.of("sales", "dbo.[vw$Sales]"));

        assertEquals("SUM(dbo.[vw$Sales].NetAmount)", resolved.getSql());
    }

    @Test
    void nothingAtAllResolvesToNothingUsableRatherThanThrowing() {
        assertFalse(AiMetricResolver.resolve(null, BINDINGS).usable());
        assertTrue(AiMetricResolver.resolveUsable(null, BINDINGS).isEmpty());
        assertTrue(AiMetricResolver.resolveUsable(new AiConceptLibrary(), null).isEmpty());
    }

    private static AiMetric monthlySales() {
        AiMetric metric = new AiMetric();
        metric.setId("monthly_sales");
        metric.setName("فروش ماهیانه");
        metric.setAliases(List.of("فروش ماه", "monthly sales"));
        metric.setDescription("مبلغ خالص فاکتورهای فروش تأییدشده");
        metric.setGrain("یک سطر به‌ازای هر ماه");
        metric.setRequires(List.of("sales"));
        metric.setSql("SUM({sales}.NetAmount)");
        metric.setFilter("{sales}.DocType = 'INV' AND {sales}.IsVoid = 0");
        metric.setTimeColumn("{sales}.DocDate");
        return metric;
    }
}
