package com.fongmi.android.tv.player.subtitle;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class AssPacketTest {
    private static final List<byte[]> INIT = List.of(
            "Format: Start, End, ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text".getBytes(StandardCharsets.UTF_8),
            "[Script Info]\nScriptType: v4.00+\nPlayResX: 640\nPlayResY: 360\n[Events]\n".getBytes(StandardCharsets.UTF_8));

    @Test public void shiftsMatroskaPacketsAndPreservesAnimationAndCommas() {
        String packet = "Dialogue: 0:00:00:00,0:00:02:50,3,0,Default,,0,0,0,,{\\move(10,20,30,40)}Hello, world";
        String result = AssPacket.shift(packet, INIT, 5_120_000);
        assertTrue(result, result.contains("0:00:05.12,0:00:07.62"));
        assertTrue(result, result.endsWith("{\\move(10,20,30,40)}Hello, world\n"));
    }
    @Test public void shiftsFullFileUsingItsEventFormat() {
        String input = "[V4+ Styles]\nFormat: Name, Fontname\nStyle: Default,Roboto\n[Events]\nFormat: Layer, Start, End, Text\nDialogue: 0,0:00:01.25,0:00:04.00,{\\i1}Test";
        String result = AssPacket.shift(input, List.of(), 10_000_000);
        assertTrue(result.contains("Style: Default,Roboto"));
        assertTrue(result, result.contains("Dialogue: 0,0:00:11.25,0:00:14.00,{\\i1}Test"));
    }
    @Test public void acceptsCentisecondsAndMilliseconds() {
        assertEquals(3_723_450, AssPacket.parseTime("1:02:03.45"));
        assertEquals(3_723_456, AssPacket.parseTime("1:02:03.456"));
        assertEquals(-1, AssPacket.parseTime("invalid"));
    }
    @Test public void retainsMalformedPacketsWithoutCrashing() {
        assertEquals("Dialogue: bad\n", AssPacket.shift("Dialogue: bad", INIT, 10));
    }
    @Test public void headerRetainsStylesAndProvidesExtractorEventFormat() {
        String header = AssPacket.header(INIT);
        assertTrue(header.contains("PlayResX: 640"));
        assertTrue(header.endsWith("Format: Start, End, ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"));
    }
    @Test public void durationUsesLastEndRatherThanLastStartOrLineOrder() {
        String text = "[Events]\nFormat: End, Start, Text\nDialogue: 0:01:10.00,0:00:00.00,Long overlap\nDialogue: 0:00:20.00,0:00:05.00,Short animation";
        assertEquals(70_000_000, AssPacket.durationUs(text.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void readsUtf16DurationWithBom() {
        String text = "\uFEFF[Events]\nFormat: Start, End, Text\nDialogue: 0:00:01.00,0:00:03.50,字幕";
        assertEquals(3_500_000, AssPacket.durationUs(text.getBytes(StandardCharsets.UTF_16LE)));
    }
    @Test public void missingDialogueDoesNotInventDuration() {
        assertEquals(androidx.media3.common.C.TIME_UNSET, AssPacket.durationUs("[Events]\nDialogue: bad".getBytes(StandardCharsets.UTF_8)));
    }
}
