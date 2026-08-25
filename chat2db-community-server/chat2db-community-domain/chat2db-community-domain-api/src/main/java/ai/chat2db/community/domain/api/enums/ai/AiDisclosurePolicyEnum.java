package ai.chat2db.community.domain.api.enums.ai;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * How much of a connection's data may reach the model provider.
 *
 * <p>Asking a question sends business data to somebody else's server. That is
 * the trade the whole assistant rests on, and until now it was made once, in
 * code, for every connection alike: fifty rows of whatever came back, to
 * whichever gateway was configured. A finance database and a demo database
 * were treated the same because nothing could tell them apart.
 *
 * <p>This is the setting that tells them apart. It belongs to the connection,
 * because that is where the sensitivity lives - not to the account, not to the
 * model, and not to the question.
 *
 * <p>The three levels are deliberately few. A person choosing between four
 * shades of partial disclosure is a person who will pick the default; three
 * are distinguishable enough to be chosen on purpose.
 */
public enum AiDisclosurePolicyEnum {

    /**
     * Nothing but the shape. The assistant is told how many rows came back and
     * under which column names, and no value at all.
     *
     * <p>It can still write SQL, read the schema and explain what a query
     * would do - everything except read the answer. Worth choosing when the
     * rows themselves are the sensitive thing and the SQL is not.
     */
    NONE,

    /**
     * Up to a page of rows, which is what the product did before this existed.
     * The default, and the level the row cap already implied.
     */
    SAMPLE,

    /**
     * Every row the query returned, up to the platform's own cap. For a
     * database whose contents are not sensitive, or a gateway that is not a
     * third party.
     */
    FULL;

    /** What a connection gets when nobody has chosen. */
    public static final AiDisclosurePolicyEnum DEFAULT = SAMPLE;

    /**
     * The policy named by a stored string.
     *
     * <p>Blank means the connection predates the setting, and an unrecognised
     * name means somebody edited the file by hand or a future version wrote
     * something this one does not know. Both get the default rather than an
     * exception: a connection is not worth breaking over the spelling of a
     * setting, and the default is the behaviour that was already in place.
     */
    public static AiDisclosurePolicyEnum of(String name) {
        if (StringUtils.isBlank(name)) {
            return DEFAULT;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownName) {
            return DEFAULT;
        }
    }

    /** Whether result values may be sent at all under this policy. */
    public boolean sharesValues() {
        return this != NONE;
    }
}
