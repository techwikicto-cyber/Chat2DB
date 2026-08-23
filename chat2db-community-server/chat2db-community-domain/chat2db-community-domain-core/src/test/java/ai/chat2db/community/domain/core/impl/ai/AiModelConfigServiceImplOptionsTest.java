package ai.chat2db.community.domain.core.impl.ai;

import java.nio.file.Path;
import java.util.List;

import ai.chat2db.community.domain.api.model.ai.AiModelOptionItem;
import ai.chat2db.community.domain.api.model.request.ai.AiModelConfigSaveRequest;
import ai.chat2db.community.domain.core.converter.AiModelConfigConverter;
import ai.chat2db.community.tools.security.AesGcmUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the model picker is offered.
 *
 * The preset names - gpt-5.2, the Claude and Gemini models - belong to a hosted
 * service that supplies the credentials for them. Community has no such
 * service, so listing them shows models nobody added and nothing stands behind.
 */
class AiModelConfigServiceImplOptionsTest {

    private static final String RUNTIME_MODE = "chat2db.runtime.mode";
    private static final long USER_ID = 42L;

    @TempDir
    Path tempDirectory;

    private String originalMode;

    @BeforeEach
    void rememberMode() {
        originalMode = System.getProperty(RUNTIME_MODE);
    }

    @AfterEach
    void restoreMode() {
        if (originalMode == null) {
            System.clearProperty(RUNTIME_MODE);
        } else {
            System.setProperty(RUNTIME_MODE, originalMode);
        }
    }

    @Test
    void communityOffersOnlyTheModelsSomebodyActuallyAdded() {
        System.setProperty(RUNTIME_MODE, "community");
        AiModelConfigServiceImpl service = service();
        service.saveCurrentUserConfig(saveRequest("My gateway", "gpt-oss-120b"));

        List<AiModelOptionItem> options = service.listModelOptions();

        assertEquals(1, options.size(), options.toString());
        assertEquals("My gateway", options.get(0).getLabel());
        assertTrue(options.stream().allMatch(option -> Boolean.TRUE.equals(option.getCustomOption())));
    }

    @Test
    void communityWithNothingAddedOffersNothing() {
        System.setProperty(RUNTIME_MODE, "community");

        // Rather than a list of names that cannot be used - the picker's own
        // empty state then says to add one.
        assertTrue(service().listModelOptions().isEmpty());
    }

    @Test
    void otherEditionsStillGetThePresets() {
        System.setProperty(RUNTIME_MODE, "desktop");

        List<AiModelOptionItem> options = service().listModelOptions();

        assertTrue(options.stream().anyMatch(option -> Boolean.FALSE.equals(option.getCustomOption())),
                "the hosted presets disappeared from a build that has them");
    }

    private AiModelConfigServiceImpl service() {
        return new AiModelConfigServiceImpl(new ObjectMapper().findAndRegisterModules(), new AiModelConfigConverter(),
                () -> USER_ID, tempDirectory.resolve("ai-model-configs.json"), null);
    }

    private static AiModelConfigSaveRequest saveRequest(String name, String model) {
        AiModelConfigSaveRequest request = new AiModelConfigSaveRequest();
        request.setName(name);
        request.setProvider("OPENAI");
        request.setModel(model);
        request.setApiKey("sk-test-1234567890");
        return request;
    }
}
