package ai.chat2db.community.domain.api.enums.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How much of a connection's data reaches the model provider.
 */
class AiDisclosurePolicyEnumTest {

    @Test
    void aConnectionThatPredatesTheSettingBehavesExactlyAsItDid() {
        // Every connection already on disk has no value stored, and the
        // product's behaviour before this setting existed was a page of rows.
        assertEquals(AiDisclosurePolicyEnum.SAMPLE, AiDisclosurePolicyEnum.of(null));
        assertEquals(AiDisclosurePolicyEnum.SAMPLE, AiDisclosurePolicyEnum.of(""));
        assertEquals(AiDisclosurePolicyEnum.SAMPLE, AiDisclosurePolicyEnum.of("   "));
        assertEquals(AiDisclosurePolicyEnum.SAMPLE, AiDisclosurePolicyEnum.DEFAULT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NONE", "none", " None ", "nOnE"})
    void aStoredNameIsReadHoweverItWasWritten(String stored) {
        assertEquals(AiDisclosurePolicyEnum.NONE, AiDisclosurePolicyEnum.of(stored));
    }

    @Test
    void anUnknownNameGetsTheDefaultRatherThanAnException() {
        // Somebody hand-edited the file, or a newer version wrote a level this
        // one has never heard of. Neither is worth breaking a connection over.
        assertEquals(AiDisclosurePolicyEnum.SAMPLE, AiDisclosurePolicyEnum.of("AGGREGATE"));
        assertEquals(AiDisclosurePolicyEnum.SAMPLE, AiDisclosurePolicyEnum.of("¯\\_(ツ)_/¯"));
    }

    @Test
    void onlyNoneWithholdsTheValues() {
        assertFalse(AiDisclosurePolicyEnum.NONE.sharesValues());
        assertTrue(AiDisclosurePolicyEnum.SAMPLE.sharesValues());
        assertTrue(AiDisclosurePolicyEnum.FULL.sharesValues());
    }
}
