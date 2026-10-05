package com.fongmi.android.tv.player.subtitle;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import static org.junit.Assert.*;

public class SubtitleFontsTest {
    @Test public void recognizesFontSignaturesAndRejectsOtherData() {
        assertTrue(SubtitleFonts.validFont(new byte[]{0,1,0,0,0,0,0,0,0,0,0,0}));
        assertTrue(SubtitleFonts.validFont(new byte[]{'O','T','T','O',0,0,0,0,0,0,0,0}));
        assertTrue(SubtitleFonts.validFont(new byte[]{'t','t','c','f',0,0,0,0,0,0,0,0}));
        assertFalse(SubtitleFonts.validFont(new byte[12]));
        assertFalse(SubtitleFonts.validFont(new byte[4]));
    }
    @Test public void readsCompleteInputOnOlderAndroidApis() throws Exception {
        byte[] input={0,1,2,3,4,5};
        assertArrayEquals(input, SubtitleFonts.readFont(new ByteArrayInputStream(input)));
    }
    @Test(expected=IOException.class) public void rejectsOversizedStreamsBeforeUnboundedAllocation() throws Exception {
        SubtitleFonts.readFont(new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] data, int offset, int length) { return length; }
        });
    }
}
