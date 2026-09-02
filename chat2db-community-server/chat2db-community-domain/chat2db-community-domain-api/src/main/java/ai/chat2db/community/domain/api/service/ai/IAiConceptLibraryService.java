package ai.chat2db.community.domain.api.service.ai;

import ai.chat2db.community.domain.api.model.ai.AiConceptLibrary;

/**
 * The one library of agreed definitions this installation runs on.
 *
 * <p>An interface because the web layer needs to read and revise it and can
 * only see this module; where it is kept is the implementation's business.
 */
public interface IAiConceptLibraryService {

    /** The library as it stands. Never null; empty before anything is defined. */
    AiConceptLibrary current();

    /**
     * Replace the library wholesale.
     *
     * <p>Wholesale because that is what a revision of a standard is. The
     * version is the caller's to set: the person who changed a definition
     * knows whether it was a correction or a change of meaning, and a number
     * guessed here would be one nobody stands behind.
     *
     * @return the library as stored, tidied.
     */
    AiConceptLibrary save(AiConceptLibrary incoming);

    /** Whether a candidate library is small enough to store. */
    boolean withinLimit(AiConceptLibrary candidate);
}
