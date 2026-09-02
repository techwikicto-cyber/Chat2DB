package ai.chat2db.community.domain.core.impl.ai;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ai.chat2db.community.domain.api.model.ai.AiConceptLibrary;
import ai.chat2db.community.domain.api.model.ai.AiConceptLibraryValidator;
import ai.chat2db.community.domain.api.model.ai.AiMetric;
import ai.chat2db.community.domain.api.model.request.ai.AiBusinessContextBuildRequest;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorageFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The definitions an installation agrees on, and how they reach the assistant.
 *
 * <p>The distinction being tested is the one the whole feature rests on: a
 * definition in the profile is advice the model may follow, and a definition
 * here is an instruction with the SQL already written. Two models of very
 * different capability should produce the same figure, and that only holds if
 * the expression reaches them resolved.
 */
class AiConceptLibraryTest {

    @TempDir
    Path directory;

    @Test
    void aLibrarySurvivesARestart() {
        Path file = directory.resolve("concepts.json");
        AiConceptLibraryStore store = new AiConceptLibraryStore(file);
        store.save(libraryWith(monthlySales()));

        // A second store over the same file is what the next start-up does.
        AiConceptLibrary reloaded = new AiConceptLibraryStore(file).current();

        assertEquals(7, reloaded.getVersion());
        assertEquals(1, reloaded.getMetrics().size());
        assertEquals("SUM({sales}.NetAmount)", reloaded.getMetrics().get(0).getSql());
        assertEquals("jalali", reloaded.getConventions().get("calendar"));
    }

    @Test
    void anUnreadableLibraryDoesNotStopTheServerStarting() throws Exception {
        // Every other feature works without it, and starting up is how the
        // operator reaches the screen where they would fix it.
        Path file = directory.resolve("broken.json");
        Files.writeString(file, "{ this is not json");

        assertTrue(new AiConceptLibraryStore(file).current().getMetrics().isEmpty());
    }

    @Test
    void aFreshInstallHasAnEmptyLibraryRatherThanNone() {
        assertEquals(0, new AiConceptLibraryStore(directory.resolve("absent.json")).current().getMetrics().size());
    }

    @Test
    void twoMetricsCannotShareAnId() {
        AiMetric duplicate = monthlySales();
        AiConceptLibrary library = libraryWith(monthlySales(), duplicate);

        List<String> problems = AiConceptLibraryValidator.problemsWith(library);

        assertTrue(problems.stream().anyMatch(p -> p.contains("share the id")), problems.toString());
    }

    @Test
    void aMetricUsingALabelItNeverDeclaredIsReported() {
        // Nothing would ask the connection to bind it, so it could never
        // resolve - and it would fail silently by simply never being offered.
        AiMetric metric = monthlySales();
        metric.setRequires(List.of("sales"));
        metric.setFilter("{sales}.IsVoid = 0 AND {branch}.Active = 1");

        List<String> problems = AiConceptLibraryValidator.problemsWith(libraryWith(metric));

        assertTrue(problems.stream().anyMatch(p -> p.contains("{branch}")), problems.toString());
    }

    @Test
    void aMetricWithNoExpressionComputesNothingAndIsReported() {
        AiMetric empty = new AiMetric();
        empty.setId("orphan");

        List<String> problems = AiConceptLibraryValidator.problemsWith(libraryWith(empty));

        assertTrue(problems.stream().anyMatch(p -> p.contains("computes nothing")), problems.toString());
    }

    @Test
    void aSoundLibraryHasNothingToReport() {
        assertTrue(AiConceptLibraryValidator.problemsWith(libraryWith(monthlySales())).isEmpty());
    }

    // ── what the assistant is actually told ──────────────────────────────

    @Test
    void aBoundMetricReachesTheModelWithItsSqlAlreadyWritten() {
        String context = contextFor(libraryWith(monthlySales()), Map.of("sales", "dbo.vw_Sales"));

        assertTrue(context.contains("SUM(dbo.vw_Sales.NetAmount)"), context);
        assertTrue(context.contains("dbo.vw_Sales.IsVoid = 0"), context);
        assertTrue(context.contains("فروش ماهیانه"), context);
        // The version travels with it, so a figure can be traced to the
        // revision that defined it.
        assertTrue(context.contains("library version 7"), context);
    }

    @Test
    void theDefinitionsAreGivenAsInstructionsNotAsBackground() {
        // The whole point. Read as background, a small model may or may not
        // follow them, and the answer would not say which happened.
        String context = contextFor(libraryWith(monthlySales()), Map.of("sales", "dbo.vw_Sales"));

        assertTrue(context.contains("They are not suggestions"), context);
        assertTrue(context.contains("do not write your own"), context);
    }

    @Test
    void aMetricThisConnectionCannotComputeIsNotMentionedAtAll() {
        // Naming a figure the platform cannot compute invites the model to
        // compute it its own way, and a number from a table it picked looks
        // exactly like a real one.
        AiMetric unbound = new AiMetric();
        unbound.setId("stock_turn");
        unbound.setName("گردش انبار");
        unbound.setRequires(List.of("inventory"));
        unbound.setSql("SUM({inventory}.Qty)");

        String context = contextFor(libraryWith(monthlySales(), unbound), Map.of("sales", "dbo.vw_Sales"));

        assertTrue(context.contains("فروش ماهیانه"), context);
        assertFalse(context.contains("گردش انبار"), context);
        assertFalse(context.contains("{inventory}"), "an unresolved placeholder must never reach the model");
    }

    @Test
    void conventionsAndGlossaryTravelEvenWithNothingBound() {
        // "Dates are Jalali" is true of the connection whether or not any
        // metric resolves, and it is exactly the sort of thing a model gets
        // wrong unprompted.
        AiConceptLibrary library = libraryWith(monthlySales());
        AiConceptLibrary.AiGlossaryEntry entry = new AiConceptLibrary.AiGlossaryEntry();
        entry.setTerm("فاکتور باطل");
        entry.setMeaning("سطری با IsVoid = 1");
        library.setGlossary(List.of(entry));

        String context = contextFor(library, Map.of());

        assertTrue(context.contains("calendar: jalali"), context);
        assertTrue(context.contains("فاکتور باطل"), context);
    }

    @Test
    void anEmptyLibraryAddsNothingToThePrompt() {
        assertNull(contextFor(new AiConceptLibrary(), Map.of()));
    }

    /** The context block a question against this connection would carry. */
    private String contextFor(AiConceptLibrary library, Map<String, String> bindings) {
        AiConceptLibraryStore store = new AiConceptLibraryStore(directory.resolve("live.json"));
        store.save(library);

        IWorkspaceStorageFacade storage = (IWorkspaceStorageFacade) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {IWorkspaceStorageFacade.class},
                (proxy, method, args) -> {
                    if (!"queryDataSourceById".equals(method.getName())) {
                        return null;
                    }
                    WorkspaceDataSource dataSource = new WorkspaceDataSource();
                    dataSource.setAiBindings(bindings);
                    return dataSource;
                });

        AiBusinessContextBuildRequest request = new AiBusinessContextBuildRequest();
        request.setDataSourceId(42L);
        return new AiBusinessContextServiceImpl(storage, store).buildStructuredContext(request);
    }

    private static AiConceptLibrary libraryWith(AiMetric... metrics) {
        AiConceptLibrary library = new AiConceptLibrary();
        library.setVersion(7);
        library.setConventions(new java.util.LinkedHashMap<>(Map.of("calendar", "jalali")));
        library.setMetrics(new java.util.ArrayList<>(List.of(metrics)));
        return library;
    }

    private static AiMetric monthlySales() {
        AiMetric metric = new AiMetric();
        metric.setId("monthly_sales");
        metric.setName("فروش ماهیانه");
        metric.setAliases(List.of("فروش ماه"));
        metric.setDescription("مبلغ خالص فاکتورهای فروش تأییدشده");
        metric.setRequires(List.of("sales"));
        metric.setSql("SUM({sales}.NetAmount)");
        metric.setFilter("{sales}.DocType = 'INV' AND {sales}.IsVoid = 0");
        metric.setTimeColumn("{sales}.DocDate");
        return metric;
    }
}
