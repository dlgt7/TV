package com.fongmi.android.tv.player.util;

import androidx.media3.common.MimeTypes;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PlayerHelperTest {
    @Test public void recognizesSupWithUppercaseExtensionAndSignedUrl() {
        assertEquals(MimeTypes.APPLICATION_PGS, PlayerHelper.getSubtitleMimeType("https://example.org/sub.SUP?token=123#download"));
    }
    @Test public void queryCannotOverrideTheRealExtension() {
        assertEquals(MimeTypes.TEXT_SSA, PlayerHelper.getSubtitleMimeType("https://example.org/sub.ASS?name=wrong.sup"));
    }
    @Test public void preservesTextSubtitleTypes() {
        assertEquals(MimeTypes.TEXT_VTT, PlayerHelper.getSubtitleMimeType("captions.VTT"));
        assertEquals(MimeTypes.APPLICATION_TTML, PlayerHelper.getSubtitleMimeType("captions.dfxp"));
        assertEquals(MimeTypes.APPLICATION_SUBRIP, PlayerHelper.getSubtitleMimeType("captions.srt"));
    }
    @Test public void emptyInputRemainsEmpty() {
        assertEquals("", PlayerHelper.getSubtitleMimeType(null));
        assertEquals("", PlayerHelper.getSubtitleMimeType(""));
    }
}
