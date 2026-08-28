package ai.chat2db.community.domain.api.model.ai;

/**
 * Failures a tool result can carry that the interface has to recognise.
 *
 * <p>A tool returns text, because text is what a model reads. Some of that
 * text is also news for the person watching, and the interface cannot be
 * asked to work out which by reading English - the wording is written for the
 * model and will change. So the few failures that are worth surfacing carry a
 * marker, and this is where both sides agree on it.
 *
 * <p>Here rather than beside the code that writes it, because the writer lives
 * in the domain and the reader in the web layer, and the web layer sees only
 * this module.
 */
public final class AiToolFailures {

    /**
     * The database could not be reached, so nothing was read from it.
     *
     * <p>Prefixes the tool result. Everything after it is the report the model
     * is meant to act on; the interface uses only the marker's presence.
     */
    public static final String DATABASE_UNREACHABLE = "DATABASE_UNREACHABLE:";

    private AiToolFailures() {
    }
}
