package ai.chat2db.community.domain.api.model.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * Guessing which view here implements which label in the library.
 *
 * <p>This exists because of a fact about the people who will use it: they build
 * every customer's warehouse themselves, with the same ETL, producing the same
 * views under the same names. So the label {@code sales} is
 * {@code dbo.vw_Sales} at nearly every site, and asking an operator to type
 * that forty times would be asking them to re-enter something the platform can
 * already see.
 *
 * <p>The guesses are proposals, never decisions. Every one lands in a form
 * beside a Test button, and an operator confirms it. That matters more than the
 * hit rate: a wrong binding produces a plausible number from the wrong table,
 * which is the worst failure this product has, and the only defence is that a
 * person looked.
 *
 * <p>Ambiguity is left unanswered rather than resolved. Two views that both
 * look like {@code sales} mean the operator knows something this does not, and
 * picking one for them would hide the choice they need to make.
 */
public final class AiBindingSuggester {

    /** Prefixes an ETL adds that say nothing about what a view holds. */
    private static final List<String> NOISE_PREFIXES = List.of("vw_", "vw", "v_", "tbl_", "tb_", "t_", "dim_", "fact_");

    private AiBindingSuggester() {
    }

    /** One label, and what the connection appears to offer for it. */
    public record Suggestion(String source, String suggested, List<String> candidates) {

        public boolean confident() {
            return StringUtils.isNotBlank(suggested);
        }
    }

    /**
     * Propose a binding for every label the library needs.
     *
     * @param sources    labels the library declares, from {@code requiredSources()}.
     * @param tableNames every table and view on the connection, qualified or not.
     * @param existing   bindings already configured, which are never overridden.
     */
    public static List<Suggestion> suggest(List<String> sources, List<String> tableNames,
            Map<String, String> existing) {
        List<Suggestion> suggestions = new ArrayList<>();
        if (sources == null) {
            return suggestions;
        }
        Map<String, List<String>> byNormalisedName = index(tableNames);

        for (String source : sources) {
            if (StringUtils.isBlank(source)) {
                continue;
            }
            String alreadyBound = existing == null ? null : StringUtils.trimToNull(existing.get(source));
            if (alreadyBound != null) {
                // Somebody already decided. Re-guessing over their answer would
                // quietly undo a correction they made for a reason.
                suggestions.add(new Suggestion(source, alreadyBound, List.of(alreadyBound)));
                continue;
            }
            // An exact match on the meaningful part of the name, and only one
            // of them, is the case a standardised warehouse produces - the one
            // worth filling in for somebody. Everything else is offered and
            // left to them.
            List<String> exact = byNormalisedName.get(normalise(source));
            if (exact != null && exact.size() == 1) {
                suggestions.add(new Suggestion(source, exact.get(0), exact));
                continue;
            }
            suggestions.add(new Suggestion(source, null, exact != null ? exact : near(source, byNormalisedName)));
        }
        return suggestions;
    }

    /** The suggestions as a binding map, keeping only the confident ones. */
    public static Map<String, String> asBindings(List<Suggestion> suggestions) {
        Map<String, String> bindings = new LinkedHashMap<>();
        for (Suggestion suggestion : suggestions) {
            if (suggestion.confident()) {
                bindings.put(suggestion.source(), suggestion.suggested());
            }
        }
        return bindings;
    }

    /**
     * Names that resemble the label without being it.
     *
     * <p>Offered, never chosen. {@code vw_SalesDetail} is a plausible answer
     * for {@code sales} and a plausible answer is not a right one - the line
     * items are not the invoices, and a figure computed from the wrong one of
     * those is wrong in a way nobody spots by looking at it.
     */
    private static List<String> near(String source, Map<String, List<String>> byNormalisedName) {
        String wanted = normalise(source);
        List<String> near = new ArrayList<>();
        byNormalisedName.forEach((name, tables) -> {
            if (name.contains(wanted) || wanted.contains(name)) {
                near.addAll(tables);
            }
        });
        return near;
    }

    private static Map<String, List<String>> index(List<String> tableNames) {
        Map<String, List<String>> index = new LinkedHashMap<>();
        if (tableNames == null) {
            return index;
        }
        for (String tableName : tableNames) {
            if (StringUtils.isBlank(tableName)) {
                continue;
            }
            index.computeIfAbsent(normalise(bareName(tableName)), key -> new ArrayList<>()).add(tableName.trim());
        }
        return index;
    }

    /** The last segment: {@code TopiaDB.dbo.vw_Sales} is about {@code vw_Sales}. */
    private static String bareName(String tableName) {
        String[] segments = tableName.trim().split("\\.");
        return segments[segments.length - 1];
    }

    /**
     * The part of a name that carries meaning: lower-cased, stripped of the
     * prefixes an ETL adds and of the separators people disagree about, so
     * {@code vw_Sales}, {@code V_SALES} and {@code sales} are one thing.
     */
    static String normalise(String name) {
        String cleaned = StringUtils.trimToEmpty(name)
                .replace("[", "").replace("]", "").replace("\"", "").replace("`", "")
                .toLowerCase(Locale.ROOT);
        for (String prefix : NOISE_PREFIXES) {
            if (cleaned.startsWith(prefix) && cleaned.length() > prefix.length()) {
                cleaned = cleaned.substring(prefix.length());
                break;
            }
        }
        return cleaned.replace("_", "").replace("-", "").replace(" ", "");
    }
}
