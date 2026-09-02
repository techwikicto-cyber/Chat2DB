package ai.chat2db.community.domain.core.impl.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import ai.chat2db.community.domain.api.model.ai.AiConceptLibrary;
import ai.chat2db.community.domain.api.model.ai.AiMetric;
import ai.chat2db.community.tools.util.ConfigUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * The one library this installation runs on.
 *
 * <p>Shared rather than per account, and deliberately so. The definitions are
 * the organisation's, not a person's: two analysts signing in to the same
 * server must be computing "monthly sales" the same way, or the word
 * "standard" means nothing. It sits beside the other things that belong to the
 * machine rather than to whoever is signed in - the installed drivers, the
 * model configuration - and the workspace migration leaves it there for the
 * same reason.
 *
 * <p>Written through a temporary file and moved into place, because a library
 * half-written during a save is one that fails to load at the next restart,
 * and the failure would arrive hours later with nothing to connect it to.
 */
@Slf4j
@Component
public class AiConceptLibraryStore {

    /**
     * A ceiling on the whole library. It is definitions, not data: a few
     * hundred metrics is a very large standard, and this is several times
     * that. Past it, something is being stored here that should not be.
     */
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final AtomicReference<AiConceptLibrary> library = new AtomicReference<>(new AiConceptLibrary());
    private final Path file;

    public AiConceptLibraryStore() {
        this(Path.of(ConfigUtils.getEnvBasePath(), "storage", "concepts.json"));
    }

    AiConceptLibraryStore(Path file) {
        this.file = file;
        load();
    }

    /** The library as it stands. Never null; empty before anything is defined. */
    public AiConceptLibrary current() {
        return library.get();
    }

    /**
     * Replace the library wholesale.
     *
     * <p>Wholesale because that is what it is: a revision of a standard, not an
     * edit to a row. The version is the caller's to set - the person who
     * changed a definition is the one who knows whether it was a correction or
     * a change of meaning, and guessing that here would put a number on the
     * audit trail that nobody stands behind.
     */
    public synchronized AiConceptLibrary save(AiConceptLibrary incoming) {
        AiConceptLibrary cleaned = normalise(incoming);
        library.set(cleaned);
        write(cleaned);
        return cleaned;
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
        labels.addAll(ai.chat2db.community.domain.api.model.ai.AiMetricResolver.placeholdersIn(metric.getSql()));
        labels.addAll(ai.chat2db.community.domain.api.model.ai.AiMetricResolver.placeholdersIn(metric.getFilter()));
        labels.addAll(ai.chat2db.community.domain.api.model.ai.AiMetricResolver
                .placeholdersIn(metric.getTimeColumn()));
        return new ArrayList<>(labels);
    }

    /** Trimmed and de-nulled, so what is stored is what a reader expects. */
    static AiConceptLibrary normalise(AiConceptLibrary incoming) {
        AiConceptLibrary cleaned = incoming == null ? new AiConceptLibrary() : incoming;
        if (cleaned.getVersion() < 1) {
            cleaned.setVersion(1);
        }
        if (cleaned.getMetrics() == null) {
            cleaned.setMetrics(new ArrayList<>());
        }
        cleaned.getMetrics().removeIf(java.util.Objects::isNull);
        for (AiMetric metric : cleaned.getMetrics()) {
            metric.setId(StringUtils.trimToEmpty(metric.getId()));
            metric.setName(StringUtils.trimToEmpty(metric.getName()));
            if (metric.getAliases() == null) {
                metric.setAliases(new ArrayList<>());
            }
            if (metric.getRequires() == null) {
                metric.setRequires(new ArrayList<>());
            }
        }
        return cleaned;
    }

    static boolean isWithinLimit(AiConceptLibrary candidate) {
        try {
            return MAPPER.writeValueAsBytes(candidate).length <= MAX_BYTES;
        } catch (Exception e) {
            return false;
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (StringUtils.isBlank(json)) {
                return;
            }
            library.set(normalise(MAPPER.readValue(json, AiConceptLibrary.class)));
        } catch (Exception e) {
            // An unreadable library must not stop the server: every other
            // feature still works without it, and starting up is how the
            // operator gets to the screen where they can fix it.
            log.error("could not read the concept library at {} - starting with an empty one", file, e);
        }
    }

    private void write(AiConceptLibrary toWrite) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, MAPPER.writeValueAsString(toWrite), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("could not write the concept library to {}", file, e);
        }
    }
}
