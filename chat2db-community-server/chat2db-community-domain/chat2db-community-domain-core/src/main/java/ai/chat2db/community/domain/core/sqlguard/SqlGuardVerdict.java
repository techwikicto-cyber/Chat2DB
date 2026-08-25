package ai.chat2db.community.domain.core.sqlguard;

import java.util.List;

/**
 * What the guard decided about one statement, and why.
 *
 * <p>Every rejection carries a stable {@code ruleId} so that a log filter, a
 * metric and the text handed back to the model can all branch on it without
 * reading English. The messages are allowed to change; the ids are not.
 */
public record SqlGuardVerdict(boolean allowed, List<Issue> issues, List<String> tables) {

    /** One reason a statement was refused. */
    public record Issue(String ruleId, String message, String hint) {
    }

    public static SqlGuardVerdict allow(List<String> tables) {
        return new SqlGuardVerdict(true, List.of(), tables);
    }

    public static SqlGuardVerdict reject(String ruleId, String message, String hint) {
        return new SqlGuardVerdict(false, List.of(new Issue(ruleId, message, hint)), List.of());
    }

    public static SqlGuardVerdict reject(List<Issue> issues) {
        return new SqlGuardVerdict(false, List.copyOf(issues), List.of());
    }

    /**
     * The refusal as the assistant should read it.
     *
     * <p>Deterministic, and it names what to do instead. A refusal the model
     * cannot act on is one it retries verbatim.
     */
    public String feedback() {
        if (allowed || issues.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder(256);
        text.append("This statement was refused before it reached the database. Fix these problems:\n");
        for (Issue issue : issues) {
            text.append("- [").append(issue.ruleId()).append("] ").append(issue.message());
            if (issue.hint() != null && !issue.hint().isBlank()) {
                text.append(' ').append(issue.hint());
            }
            text.append('\n');
        }
        text.append("Return one corrected SELECT statement, or explain to the user why the question "
                + "cannot be answered by reading data.");
        return text.toString();
    }

    /** The rule ids that fired, for a log line that stays greppable. */
    public String ruleIds() {
        return String.join(",", issues.stream().map(Issue::ruleId).toList());
    }
}
