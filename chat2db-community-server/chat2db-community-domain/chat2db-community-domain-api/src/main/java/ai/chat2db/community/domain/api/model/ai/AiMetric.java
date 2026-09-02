package ai.chat2db.community.domain.api.model.ai;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * One agreed figure, defined once and computed the same way everywhere.
 *
 * <p>A definition written as prose in a connection's profile is advice: the
 * model reads it and decides what to do. A big model usually decides well and a
 * small one sometimes forgets a clause, and nothing about the answer says which
 * happened - the number just comes out slightly wrong and nobody notices for a
 * quarter. That is not a acceptable way to compute a figure somebody signs off
 * on.
 *
 * <p>A metric is the same sentence made executable. The expression below is
 * substituted into the query by the platform, and the guard checks afterwards
 * that it survived. Two models of very different capability then produce the
 * same number, because neither of them decided what the number meant.
 *
 * <h2>Why the SQL names no tables</h2>
 *
 * <p>{@link #requires} declares labels - {@code sales}, {@code customer} - and
 * the expression refers to them as <code>{sales}</code>. What each label points
 * at is a property of the connection, not of the definition, so one definition
 * is usable at every customer whose warehouse was built to the same shape.
 * That is the whole reason the library is portable: the concept travels, the
 * physical table does not.
 */
@Data
public class AiMetric {

    /** Stable name used in bindings, references and the audit trail. */
    private String id;

    /** What a person calls it, in their own language. */
    private String name;

    /**
     * Other things people call it, so a question can be recognised.
     *
     * <p>Matched literally rather than by similarity: the vocabulary here is
     * one the finance team controls, and for a controlled vocabulary an exact
     * match is both more accurate and possible to debug when it misses.
     */
    private List<String> aliases = new ArrayList<>();

    /** What it means, for the person choosing it and for the model reading it. */
    private String description;

    /** What one row of the result represents, when that is not obvious. */
    private String grain;

    /** The labels this metric's expressions refer to, e.g. {@code ["sales"]}. */
    private List<String> requires = new ArrayList<>();

    /** The measure, e.g. {@code SUM({sales}.NetAmount)}. */
    private String sql;

    /** Rows that must be excluded, e.g. {@code {sales}.IsVoid = 0}. Optional. */
    private String filter;

    /** The column a time range applies to, e.g. {@code {sales}.DocDate}. Optional. */
    private String timeColumn;

    /** Off without being deleted: a definition retired but not forgotten. */
    private boolean enabled = true;
}
