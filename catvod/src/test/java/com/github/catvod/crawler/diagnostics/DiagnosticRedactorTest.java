package com.github.catvod.crawler.diagnostics;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiagnosticRedactorTest {
    @Test public void hidesUrlCredentialsAndAllQueryValuesIncludingEncodedKeys() {
        String safe = DiagnosticRedactor.redact("request https://user:pass@example.test/movie?q=title&%74oken=abc#private");
        assertEquals("request https://[REDACTED]@example.test/movie?[REDACTED]", safe);
    }

    @Test public void hidesHeadersQuotedJsonAndNestedSecrets() {
        String safe = DiagnosticRedactor.redact("Authorization: Bearer HEADER\nCookie: a=COOKIE1; b=COOKIE2\n"
                + "{\"customSecret\": {\"nested\": [\"ONE\", {\"more\":\"TWO\"}]}, \"password\":\"escaped\\\"SECRET\", \"ok\":12}");
        for (String secret : new String[]{"HEADER", "COOKIE1", "COOKIE2", "ONE", "TWO", "SECRET"}) assertFalse(safe, safe.contains(secret));
        assertTrue(safe, safe.contains("\"ok\":12"));
    }

    @Test public void hidesStandaloneBearerAndSignedTokenPath() {
        String safe = DiagnosticRedactor.redact("Bearer FREE_TOKEN https://host.test/token/PATH_SECRET/play");
        assertFalse(safe.contains("FREE_TOKEN"));
        assertFalse(safe.contains("PATH_SECRET"));
        assertTrue(safe.contains("/play"));
    }

    @Test public void hidesFoldedAuthorizationAndCookieHeaders() {
        String safe = DiagnosticRedactor.redact("Authorization: Bearer\r\nTOKEN_VALUE\nCookie: a=FIRST\r\n b=SECOND\nready");
        assertFalse(safe, safe.contains("TOKEN_VALUE"));
        assertFalse(safe, safe.contains("FIRST"));
        assertFalse(safe, safe.contains("SECOND"));
        assertTrue(safe, safe.contains("ready"));
    }

    @Test(timeout = 1000) public void handlesAdversarialLongWordsWithoutRegexBacktracking() {
        String value = "auth".repeat(8192);
        assertEquals(value, DiagnosticRedactor.redact(value));
    }

    @Test public void hidesJsonKeysWithSpacesAndUnicodeEscapes() {
        String safe = DiagnosticRedactor.redact("{\"client secret\":\"VALUE_ONE\",\"\\u0074oken\":\"VALUE_TWO\"}");
        assertFalse(safe, safe.contains("VALUE_ONE"));
        assertFalse(safe, safe.contains("VALUE_TWO"));
    }

    @Test public void hidesAssignmentsInsideQuotedLogMessages() {
        String safe = DiagnosticRedactor.redact("message=\"login password=PRIVATE failed\"");
        assertFalse(safe, safe.contains("PRIVATE"));
    }

    @Test public void repeatedRedactionIsStableAndDoesNotEraseFollowingEvents() {
        String once = DiagnosticRedactor.redact("access_token=VALUE; count=2\nsecret=[1,2]\nplayback ready");
        assertEquals(once, DiagnosticRedactor.redact(once));
        assertTrue(once, once.contains("count=2"));
        assertTrue(once, once.contains("playback ready"));
    }

    @Test public void clampsHugeAndUnterminatedValues() {
        String safe = DiagnosticRedactor.redact("password=\"" + "s".repeat(1000000));
        assertEquals("password=[REDACTED]", safe);
    }
}
