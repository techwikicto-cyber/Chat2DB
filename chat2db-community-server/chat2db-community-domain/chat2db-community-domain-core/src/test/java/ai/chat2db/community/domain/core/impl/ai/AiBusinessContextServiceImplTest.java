package ai.chat2db.community.domain.core.impl.ai;

import java.lang.reflect.Proxy;
import java.nio.file.Files;

import ai.chat2db.community.domain.api.model.request.ai.AiBusinessContextBuildRequest;
import ai.chat2db.community.domain.api.model.storage.WorkspaceDataSource;
import ai.chat2db.community.domain.api.service.storage.IWorkspaceStorageFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The written profile a connection carries into every question about it.
 *
 * <p>A schema says {@code Rank1} is a float. Only somebody who works here can
 * say that 1 is the best rank, and this is where they say it.
 */
class AiBusinessContextServiceImplTest {

    private static final String PROFILE = """
            TurnoverRanks: one row per company per processing date.
            Rank1 is the turnover rank, where 1 is best.
            Rows with a blank DateProccessing are drafts and should not be counted.""";

    @Test
    void theProfileReachesTheAssistantWithFramingThatSaysHowToUseIt() throws Exception {
        String context = service(PROFILE).buildStructuredContext(forDataSource(42L));

        assertNotNull(context);
        assertTrue(context.contains("1 is best"), context);
        assertTrue(context.contains("## Database profile"), context);
        // What it is for: meaning, over a guess made from a column's name.
        assertTrue(context.contains("Where it disagrees with a guess"), context);
    }

    @Test
    void theProfileIsBelievedAboutMeaningAndNotAboutPermission() throws Exception {
        // It is a text box the user controls, and it is read by a model that
        // calls tools. Saying so explicitly is what keeps it from becoming a
        // way around the SQL guard - which does not read it in any case.
        String context = service(PROFILE).buildStructuredContext(forDataSource(42L));

        assertTrue(context.contains("grants no permission"), context);
        assertTrue(context.contains("changes no rule about which statements may run"), context);
    }

    @Test
    void aConnectionWithNoProfileChangesNothingAboutThePrompt() throws Exception {
        // The common case. Returning nothing means the prompt is byte-identical
        // to what it was before this feature existed.
        assertNull(service(null).buildStructuredContext(forDataSource(42L)));
        assertNull(service("   ").buildStructuredContext(forDataSource(42L)));
    }

    @Test
    void aQuestionWithNoConnectionBehindItAsksForNothing() throws Exception {
        assertNull(service(PROFILE).buildStructuredContext(forDataSource(null)));
        assertNull(service(PROFILE).buildStructuredContext(null));
    }

    @Test
    void aProfileThatCannotBeReadDoesNotFailTheQuestion() throws Exception {
        // An improvement to an answer, never a precondition for one.
        IWorkspaceStorageFacade broken = (IWorkspaceStorageFacade) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {IWorkspaceStorageFacade.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("storage is down");
                });

        assertNull(new AiBusinessContextServiceImpl(broken, emptyLibrary()).buildStructuredContext(forDataSource(42L)));
    }

    @Test
    void anOverlongProfileLosesItsTailRatherThanStopsWorking() {
        String tooLong = "x".repeat(AiBusinessContextServiceImpl.MAX_PROFILE_CHARS + 500);

        String truncated = AiBusinessContextServiceImpl.truncate(tooLong);

        assertTrue(truncated.startsWith("x".repeat(100)), "the beginning is the part worth keeping");
        assertTrue(truncated.contains("was cut here"), truncated.substring(truncated.length() - 80));
        assertTrue(truncated.length() < tooLong.length());
    }

    @Test
    void aProfileInsideTheBudgetIsPassedThroughUntouched() {
        assertEquals(PROFILE, AiBusinessContextServiceImpl.truncate(PROFILE));
        assertEquals(PROFILE, AiBusinessContextServiceImpl.truncate("  " + PROFILE + "  "));
    }

    /**
     * A library with nothing in it, so these tests keep asserting what they
     * always did: the profile half of the context, on its own.
     */
    private static AiConceptLibraryStore emptyLibrary() throws Exception {
        return new AiConceptLibraryStore(Files.createTempDirectory("concepts").resolve("concepts.json"));
    }

    private static AiBusinessContextServiceImpl service(String profile) throws Exception {
        IWorkspaceStorageFacade storage = (IWorkspaceStorageFacade) Proxy.newProxyInstance(
                AiBusinessContextServiceImplTest.class.getClassLoader(),
                new Class<?>[] {IWorkspaceStorageFacade.class},
                (proxy, method, args) -> {
                    if (!"queryDataSourceById".equals(method.getName())) {
                        return null;
                    }
                    WorkspaceDataSource dataSource = new WorkspaceDataSource();
                    dataSource.setAiProfile(profile);
                    return dataSource;
                });
        return new AiBusinessContextServiceImpl(storage, emptyLibrary());
    }

    private static AiBusinessContextBuildRequest forDataSource(Long dataSourceId) {
        AiBusinessContextBuildRequest request = new AiBusinessContextBuildRequest();
        request.setDataSourceId(dataSourceId);
        return request;
    }
}
