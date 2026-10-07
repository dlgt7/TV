package com.fongmi.android.tv.update;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpdatePolicyTest {

    private JSONObject popup() throws Exception {
        return new JSONObject().put("enabled", true).put("code", 1000).put("minCode", 500)
                .put("maxCode", 900).put("expiresAt", 2000000000L);
    }

    private UpdatePolicy parse(JSONObject popup) throws Exception {
        return UpdatePolicy.parse(new JSONObject().put("schema", 1).put("popup", popup).toString());
    }

    @Test
    public void ordinaryReleasesNeverPromptByDefault() throws Exception {
        assertFalse(UpdatePolicy.parse("{\"schema\":1}").shouldPrompt(555, 1000, 0, 1000000000));
        assertFalse(parse(new JSONObject().put("enabled", false)).shouldPrompt(555, 1000, 0, 1000000000));
    }

    @Test
    public void explicitImportantReleasePromptsOnlyWithinInclusiveInstalledRange() throws Exception {
        UpdatePolicy policy = parse(popup());
        assertTrue(policy.shouldPrompt(500, 1000, 0, 1000000000));
        assertTrue(policy.shouldPrompt(555, 1000, 0, 1000000000));
        assertTrue(policy.shouldPrompt(900, 1000, 0, 1000000000));
        assertFalse(policy.shouldPrompt(499, 1000, 0, 1000000000));
        assertFalse(policy.shouldPrompt(901, 1000, 0, 1000000000));
    }

    @Test
    public void zeroBoundsDisableOnlyTheirRespectiveLimits() throws Exception {
        assertTrue(parse(popup().put("minCode", 0)).shouldPrompt(1, 1000, 0, 1000000000));
        assertTrue(parse(popup().put("maxCode", 0)).shouldPrompt(999, 1000, 0, 1000000000));
        UpdatePolicy unlimited = parse(popup().put("minCode", 0).put("maxCode", 0));
        assertTrue(unlimited.shouldPrompt(1, 1000, 0, 1000000000));
        assertTrue(unlimited.shouldPrompt(999, 1000, 0, 1000000000));
    }

    @Test
    public void neverPromptsForOldEqualDifferentOrPreviouslyShownRelease() throws Exception {
        UpdatePolicy policy = parse(popup().put("minCode", 0).put("maxCode", 0));
        assertFalse(policy.shouldPrompt(1000, 1000, 0, 1000000000));
        assertFalse(policy.shouldPrompt(1001, 1000, 0, 1000000000));
        assertFalse(policy.shouldPrompt(555, 1001, 0, 1000000000));
        assertFalse(policy.shouldPrompt(555, 999, 0, 1000000000));
        assertFalse(policy.shouldPrompt(555, 1000, 1000, 1000000000));
        assertTrue(policy.shouldPrompt(555, 1000, 999, 1000000000));
    }

    @Test
    public void expiredPolicyDoesNotPromptEvenAtExactExpiry() throws Exception {
        UpdatePolicy policy = parse(popup());
        assertTrue(policy.shouldPrompt(555, 1000, 0, 1999999999));
        assertFalse(policy.shouldPrompt(555, 1000, 0, 2000000000));
        assertFalse(policy.shouldPrompt(555, 1000, 0, 2000000001));
    }

    @Test
    public void forceCannotEnablePrompting() throws Exception {
        assertFalse(UpdatePolicy.parse("{\"schema\":1,\"force\":true}").shouldPrompt(555, 1000, 0, 1000000000));
        assertFalse(parse(new JSONObject().put("enabled", false).put("force", true))
                .shouldPrompt(555, 1000, 0, 1000000000));
    }

    @Test
    public void enabledRequiresLiteralBoolean() throws Exception {
        for (Object enabled : new Object[]{"true", "false", 1, 0, JSONObject.NULL}) {
            JSONObject popup = popup().put("enabled", enabled);
            assertThrows(IOException.class, () -> parse(popup));
        }
    }

    @Test
    public void enabledPolicyRequiresEverySafetyField() throws Exception {
        for (String field : new String[]{"enabled", "code", "minCode", "maxCode", "expiresAt"}) {
            JSONObject popup = popup();
            popup.remove(field);
            assertThrows(field, IOException.class, () -> parse(popup));
        }
    }

    @Test
    public void rejectsStringsFractionsOverflowAndNegativeVersionFields() throws Exception {
        for (String field : new String[]{"code", "minCode", "maxCode", "expiresAt"}) {
            for (Object invalid : new Object[]{"1000", 1.5, true, -1, JSONObject.NULL}) {
                JSONObject popup = popup().put(field, invalid);
                assertThrows(field, IOException.class, () -> parse(popup));
            }
        }
        for (String field : new String[]{"code", "minCode", "maxCode"}) {
            JSONObject popup = popup().put(field, 2147483648L);
            assertThrows(field, IOException.class, () -> parse(popup));
        }
        assertThrows(IOException.class, () -> parse(popup().put("code", 0)));
        assertThrows(IOException.class, () -> parse(popup().put("expiresAt", 0)));
        assertThrows(IOException.class, () -> parse(popup().put("minCode", 901)));
    }

    @Test
    public void rejectsInvalidSchemaMalformedBodiesAndNonObjectPopup() {
        for (String json : new String[]{null, "", "<html>Unavailable</html>", "[]", "{}", "{\"schema\":2}",
                "{\"schema\":\"1\"}", "{\"schema\":1,\"popup\":true}", "{\"schema\":1,\"popup\":null}"}) {
            assertThrows(IOException.class, () -> UpdatePolicy.parse(json));
        }
    }
}
