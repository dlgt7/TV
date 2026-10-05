package com.fongmi.android.tv.player.subtitle;

import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.SubtitleParser;

import java.io.IOException;

/** Reads bounded font attachments using the extractor's extension hooks, without reflection. */
public final class FontMatroskaExtractor extends MatroskaExtractor {
    private final SubtitleFonts fonts;
    private String name;
    private String mime;

    public FontMatroskaExtractor(SubtitleParser.Factory parser, SubtitleFonts fonts) {
        super(parser, 0);
        this.fonts = fonts;
    }

    @Override protected int getElementType(int id) {
        return switch (id) {
            case 0x1941A469, 0x61A7 -> 1;
            case 0x466E, 0x4660 -> 3;
            case 0x465C -> 4;
            default -> super.getElementType(id);
        };
    }
    @Override protected boolean isLevel1Element(int id) { return id == 0x1941A469 || super.isLevel1Element(id); }
    @Override protected void startMasterElement(int id, long position, long size) throws androidx.media3.common.ParserException {
        if (id == 0x61A7) { name = null; mime = null; }
        else super.startMasterElement(id, position, size);
    }
    @Override protected void stringElement(int id, String value) throws androidx.media3.common.ParserException {
        if (id == 0x466E) name = value;
        else if (id == 0x4660) mime = value;
        else super.stringElement(id, value);
    }
    @Override protected void binaryElement(int id, int size, ExtractorInput input) throws IOException {
        if (id != 0x465C) { super.binaryElement(id, size, input); return; }
        if (size < 12 || size > 24 * 1024 * 1024 || name == null || mime == null
                || !(mime.startsWith("font/") || mime.contains("font") || mime.contains("opentype"))) {
            input.skipFully(size);
            return;
        }
        byte[] data = new byte[size];
        input.readFully(data, 0, size);
        if (SubtitleFonts.validFont(data)) fonts.add(name, data);
    }
}
