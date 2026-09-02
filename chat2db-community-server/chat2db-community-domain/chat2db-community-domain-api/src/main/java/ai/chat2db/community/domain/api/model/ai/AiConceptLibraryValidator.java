package ai.chat2db.community.domain.api.model.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Whether a library is sound enough to store.
 *
 * <p>Structural only: that a metric has an id, that ids do not collide, that
 * the expressions refer to labels the metric declared. Whether the SQL computes
 * the right figure is a question for the Test button and a real database, and
 * nothing here can answer it.
 *
 * <p>Pure, and in this module rather than beside the store, because the screen
 * that authors a library needs the same answer the save path does - and it
 * should get it before the save, not as a rejection afterwards.
 */
public final class AiConceptLibraryValidator {

    private AiConceptLibraryValidator() {
    }

    /**
     * What is wrong with a library, in the order a person would fix it.
     *
     * <p>Structural only: that a metric has an id, that ids do not collide,
     * that the SQL refers to labels the metric declared. Whether the SQL
     * computes the right figure is a question for the test button and a
     * database, not for this.
     */
    public static List<String> problemsWith(AiConceptLibrary candidate) {
        List<String> problems = new ArrayList<>();
        if (candidate == null) {
            problems.add("The library is empty.");
            return problems;
        }
        Set<String> seen = new LinkedHashSet<>();
        List<AiMetric> metrics = candidate.getMetrics() == null ? List.of() : candidate.getMetrics();
        for (int index = 0; index < metrics.size(); index++) {
            AiMetric metric = metrics.get(index);
            String where = "metric " + (index + 1);
            if (metric == null) {
                problems.add(where + " is empty.");
                continue;
            }
            String id = StringUtils.trimToEmpty(metric.getId());
            if (id.isEmpty()) {
                problems.add(where + " has no id.");
            } else if (!seen.add(id.toLowerCase(Locale.ROOT))) {
                problems.add("Two metrics share the id '" + id + "'. An id has to name one figure.");
            }
            if (StringUtils.isBlank(metric.getSql())) {
                problems.add((id.isEmpty() ? where : "'" + id + "'") + " has no expression, so it computes nothing.");
                continue;
            }
            // Every label the expressions use has to be declared, or a
            // connection has no way to know what to bind.
            List<String> declared = metric.getRequires() == null ? List.of() : metric.getRequires();
            for (String used : usedLabels(metric)) {
                if (!declared.contains(used)) {
                    problems.add("'" + id + "' uses {" + used + "} without listing it in requires, "
                            + "so no connection will be asked to bind it.");
                }
            }
        }
        return problems;
    }

    private static List<String> usedLabels(AiMetric metric) {
        Set<String> labels = new LinkedHashSet<>();
        labels.addAll(AiMetricResolver.placeholdersIn(metric.getSql()));
        labels.addAll(AiMetricResolver.placeholdersIn(metric.getFilter()));
        labels.addAll(AiMetricResolver.placeholdersIn(metric.getTimeColumn()));
        return new ArrayList<>(labels);
    }
}
