package ai.chat2db.community.domain.api.model.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * Turning a portable definition into SQL that runs here.
 *
 * <p>A metric says <code>SUM({sales}.NetAmount)</code>. A connection says
 * {@code sales} means {@code dbo.vw_Sales}. This puts the two together, and
 * refuses to do it half way: a metric with a label nothing is bound to
 * resolves to nothing at all rather than to SQL with a hole in it.
 *
 * <p>That refusal is the important behaviour. The alternative - handing the
 * model a definition it cannot execute and letting it improvise the missing
 * table - produces a number, and a number from an improvised table is worse
 * than no number, because it looks exactly like a real one.
 *
 * <p>Pure: no storage, no connection, no model. Everything it needs is in its
 * arguments, which is what lets the whole substitution be tested without a
 * database anywhere near it.
 */
public final class AiMetricResolver {

    /** <code>{label}</code>, where a label is a word a binding can name. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private AiMetricResolver() {
    }

    /** A metric with its placeholders filled in, or the reason it could not be. */
    @Data
    public static class Resolved {
        private String metricId;
        private String name;
        private String description;
        private String grain;
        private String sql;
        private String filter;
        private String timeColumn;
        /** Labels with nothing bound to them. Non-empty means unusable. */
        private List<String> missingSources = new ArrayList<>();

        public boolean usable() {
            return missingSources.isEmpty() && StringUtils.isNotBlank(sql);
        }

        /** The measure and its filter as one condition-bearing phrase. */
        public String expression() {
            return StringUtils.isBlank(filter) ? sql : sql + "  [only where: " + filter + "]";
        }
    }

    /**
     * Resolve one metric against one connection's bindings.
     *
     * @param metric   the portable definition.
     * @param bindings label to physical table, as configured on the connection.
     * @return the resolution, usable or not; never null.
     */
    public static Resolved resolve(AiMetric metric, Map<String, String> bindings) {
        Resolved resolved = new Resolved();
        if (metric == null) {
            return resolved;
        }
        resolved.setMetricId(metric.getId());
        resolved.setName(metric.getName());
        resolved.setDescription(metric.getDescription());
        resolved.setGrain(metric.getGrain());

        Set<String> missing = new LinkedHashSet<>();
        resolved.setSql(substitute(metric.getSql(), bindings, missing));
        resolved.setFilter(substitute(metric.getFilter(), bindings, missing));
        resolved.setTimeColumn(substitute(metric.getTimeColumn(), bindings, missing));

        // A label declared but never used is still a promise the connection has
        // not kept, and the operator should see it in the same place as the rest.
        if (metric.getRequires() != null) {
            for (String source : metric.getRequires()) {
                if (StringUtils.isNotBlank(source) && StringUtils.isBlank(binding(bindings, source))) {
                    missing.add(source);
                }
            }
        }
        resolved.setMissingSources(new ArrayList<>(missing));
        return resolved;
    }

    /** Every metric in the library that this connection can actually compute. */
    public static List<Resolved> resolveUsable(AiConceptLibrary library, Map<String, String> bindings) {
        List<Resolved> usable = new ArrayList<>();
        if (library == null || library.getMetrics() == null) {
            return usable;
        }
        for (AiMetric metric : library.getMetrics()) {
            if (metric == null || !metric.isEnabled()) {
                continue;
            }
            Resolved resolved = resolve(metric, bindings);
            if (resolved.usable()) {
                usable.add(resolved);
            }
        }
        return usable;
    }

    /** The labels a piece of SQL refers to. */
    public static List<String> placeholdersIn(String sql) {
        List<String> found = new ArrayList<>();
        if (StringUtils.isBlank(sql)) {
            return found;
        }
        Matcher matcher = PLACEHOLDER.matcher(sql);
        while (matcher.find()) {
            String label = matcher.group(1);
            if (!found.contains(label)) {
                found.add(label);
            }
        }
        return found;
    }

    private static String substitute(String sql, Map<String, String> bindings, Set<String> missing) {
        if (StringUtils.isBlank(sql)) {
            return sql;
        }
        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuilder out = new StringBuilder(sql.length() + 32);
        while (matcher.find()) {
            String label = matcher.group(1);
            String table = binding(bindings, label);
            if (StringUtils.isBlank(table)) {
                missing.add(label);
                // Left as it was: a resolution that failed must not read as one
                // that succeeded, and `usable()` is what the caller checks.
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(table));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String binding(Map<String, String> bindings, String label) {
        return bindings == null ? null : StringUtils.trimToNull(bindings.get(label));
    }
}
