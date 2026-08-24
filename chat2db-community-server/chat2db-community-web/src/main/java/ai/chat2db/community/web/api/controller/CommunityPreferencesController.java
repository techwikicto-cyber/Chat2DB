package ai.chat2db.community.web.api.controller;

import java.util.Map;

import ai.chat2db.community.tools.util.CommunityIdentity;
import ai.chat2db.community.tools.wrapper.result.ActionResult;
import ai.chat2db.community.tools.wrapper.result.DataResult;
import ai.chat2db.community.web.api.config.web.auth.CommunityAuthSupport;
import ai.chat2db.community.web.api.config.web.auth.CommunityPreferencesStore;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in account's own settings.
 *
 * The account is taken from the session, never from the request, so this reads
 * and writes one person's settings and there is no parameter that could be
 * changed to reach anyone else's. {@code CommunityAuthFilter} has already
 * refused the call if there is no session at all.
 */
@RestController
@RequestMapping("/api/community/preferences")
public class CommunityPreferencesController {

    private final CommunityPreferencesStore preferences;

    public CommunityPreferencesController(CommunityPreferencesStore preferences) {
        this.preferences = preferences;
    }

    /**
     * Returns this account's saved settings.
     * <p>
     * Endpoint: {@code GET /api/community/preferences}.
     *
     * @return data result holding the settings, empty when none were saved.
     */
    @GetMapping("")
    public DataResult<Map<String, Object>> read() {
        String account = CommunityIdentity.currentAccount();
        if (StringUtils.isBlank(account)) {
            // Sign-in switched off: there is no account to attribute settings
            // to, so the browser keeps its own and this returns nothing.
            return DataResult.of(Map.of());
        }
        return DataResult.of(preferences.find(account));
    }

    /**
     * Replaces this account's settings with the payload.
     * <p>
     * Endpoint: {@code PUT /api/community/preferences}.
     *
     * @param body the settings to store; replaces what was there.
     * @return action result for the operation.
     */
    @PutMapping("")
    public ActionResult write(@RequestBody(required = false) Map<String, Object> body) {
        String account = CommunityIdentity.currentAccount();
        if (StringUtils.isBlank(account)) {
            return ActionResult.isSuccess();
        }
        if (!CommunityPreferencesStore.isWithinLimit(body)) {
            return CommunityAuthSupport.businessFailure("community.preferences.tooLarge");
        }
        preferences.save(account, body);
        return ActionResult.isSuccess();
    }
}
