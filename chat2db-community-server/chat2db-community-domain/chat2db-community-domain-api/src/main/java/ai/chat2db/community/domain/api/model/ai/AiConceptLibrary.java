package ai.chat2db.community.domain.api.model.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * The definitions this deployment agrees on, in one place.
 *
 * <p>One library per installation, not one per connection. That is the point of
 * it: a team whose work is standardising what "monthly sales" means across
 * forty customers cannot keep forty copies of the answer, because the day the
 * definition changes they have forty edits to make and no way to tell which
 * ones drifted. Held once, it can be exported as a file and imported at the
 * next customer, and every deployment can say which version it is on.
 *
 * <p>What is <em>not</em> here is which physical view implements each label.
 * That is the one thing that genuinely differs between customers, and it lives
 * on the connection.
 */
@Data
public class AiConceptLibrary {

    /**
     * Bumped whenever a definition changes, by whoever changes it.
     *
     * <p>It travels with every answer computed from this library. When a figure
     * from last month disagrees with the same figure today, the version is what
     * turns "the numbers are wrong" into "the definition changed in April" -
     * and there is no way to reconstruct that afterwards if it was never
     * recorded.
     */
    private int version = 1;

    /** Free note about this revision: what changed and why. */
    private String notes;

    /**
     * The rules that apply to every metric rather than to one.
     *
     * <p>Deliberately an open map. Calendar, fiscal year start, whether "last
     * month" is calendar or rolling, currency, rounding - the list is not one
     * this module should have an opinion about, and a team will need a
     * convention nobody here anticipated.
     */
    private Map<String, String> conventions = new LinkedHashMap<>();

    private List<AiMetric> metrics = new ArrayList<>();

    /** Business terms that are not figures: what a word means here. */
    private List<AiGlossaryEntry> glossary = new ArrayList<>();

    /** One business term and what it means in this organisation. */
    @Data
    public static class AiGlossaryEntry {
        private String term;
        private String meaning;
    }

    /** Every label every enabled metric needs, in the order first seen. */
    public List<String> requiredSources() {
        List<String> sources = new ArrayList<>();
        for (AiMetric metric : metrics) {
            if (metric == null || !metric.isEnabled() || metric.getRequires() == null) {
                continue;
            }
            for (String source : metric.getRequires()) {
                if (source != null && !source.isBlank() && !sources.contains(source)) {
                    sources.add(source);
                }
            }
        }
        return sources;
    }
}
