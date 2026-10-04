package com.fongmi.android.tv.player.subtitle;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.text.SubtitleDecoderFactory;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import com.fongmi.android.tv.player.track.DualSubtitleTrackSelector;
import com.fongmi.android.tv.setting.AdvancedSubtitleSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.peerless2012.ass.AssFrame;

public final class AdvancedSubtitleController {
    private record FrameUpdate(AssFrame frame, int width, int height, int generation) {}
    private final boolean nativeAss = AdvancedSubtitleSetting.ass() && AssTextRenderer.available();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.Map<android.net.Uri, SubtitleSource> fontSets = new java.util.LinkedHashMap<>();
    private volatile SubtitleSource activeFonts = new SubtitleSource();
    private android.net.Uri activeUri;
    private AssFrame primaryFrame, secondaryFrame;
    private int primaryWidth = 1, primaryHeight = 1, secondaryWidth = 1, secondaryHeight = 1;

    public synchronized void activate(androidx.media3.common.MediaItem item) {
        activeUri = item.localConfiguration.uri;
        activeFonts = fontsFor(item);
    }

    private synchronized SubtitleSource fontsFor(androidx.media3.common.MediaItem item) {
        android.net.Uri uri = item.localConfiguration.uri;
        SubtitleSource fonts = fontSets.computeIfAbsent(uri, ignored -> new SubtitleSource());
        // Current media plus one candidate; extracted fonts stay owned by their source.
        while (fontSets.size() > 2) {
            android.net.Uri discard = fontSets.keySet().stream().filter(key -> !key.equals(activeUri) && !key.equals(uri)).findFirst().orElse(null);
            if (discard == null) break;
            fontSets.remove(discard);
        }
        return fonts;
    }

    public void refreshStyle() {
        if (primaryAssView != null) primaryAssView.invalidate();
        if (secondaryAssView != null) secondaryAssView.invalidate();
        for (AssTextRenderer renderer : renderers) renderer.refresh();
    }
    private final List<AssTextRenderer> renderers = new ArrayList<>();
    private final List<DualSubtitleTrackSelector> selectors = new ArrayList<>();
    private SubtitleView host;
    private SubtitleView secondaryView;
    private AssOverlayView primaryAssView;
    private AssOverlayView secondaryAssView;
    private CueGroup secondaryCues = CueGroup.EMPTY_TIME_ZERO;
    private String secondaryFormat;
    private boolean released;

    public boolean usesNativeAss() { return nativeAss; }

    public int secondaryRendererCount() { return nativeAss ? 2 : 1; }
    public void addSelector(DualSubtitleTrackSelector selector) {
        selectors.add(selector);
        selector.setSecondary(secondaryFormat);
    }
    public void select(String format) {
        secondaryFormat = format;
        for (DualSubtitleTrackSelector selector : selectors) selector.setSecondary(format);
    }
    public String selected() { return secondaryFormat; }

    public SubtitleParser.Factory parserFactory() {
        DefaultSubtitleParserFactory normal = new DefaultSubtitleParserFactory();
        return new SubtitleParser.Factory() {
            @Override public boolean supportsFormat(Format format) { return !(nativeAss && MimeTypes.TEXT_SSA.equals(format.sampleMimeType)) && normal.supportsFormat(format); }
            @Override public int getCueReplacementBehavior(Format format) { return normal.getCueReplacementBehavior(format); }
            @Override public SubtitleParser create(Format format) { return normal.create(format); }
        };
    }

    public ExtractorsFactory extractors(ExtractorsFactory original, androidx.media3.common.MediaItem item) {
        SubtitleSource fonts = fontsFor(item);
        return () -> {
            androidx.media3.extractor.Extractor[] all = original.createExtractors();
            if (nativeAss) for (int i = 0; i < all.length; i++) {
                if (all[i] instanceof MatroskaExtractor) all[i] = new AssTrackingExtractor(new FontMatroskaExtractor(parserFactory(), fonts.fonts), fonts.history);
            }
            return all;
        };
    }

    public RenderersFactory renderers(RenderersFactory delegate) {
        return (handler, video, audio, text, metadata) -> {
            ArrayList<Renderer> all = new ArrayList<>(Arrays.asList(delegate.createRenderers(handler, video, audio, text, metadata)));
            if (nativeAss) {
                for (int i = 0; i < all.size(); i++) if (all.get(i).getTrackType() == C.TRACK_TYPE_TEXT) all.set(i, textRenderer(text));
                all.add(assRenderer(false));
            }
            all.add(textRenderer(new androidx.media3.exoplayer.text.TextOutput() {
                @Override public void onCues(CueGroup cues) {
                    secondaryCues = cues;
                    if (secondaryView != null) secondaryView.setCues(cues.cues);
                }
            }));
            if (nativeAss) all.add(assRenderer(true));
            return all.toArray(new Renderer[0]);
        };
    }

    private TextRenderer textRenderer(androidx.media3.exoplayer.text.TextOutput output) {
        SubtitleDecoderFactory decoder = new SubtitleDecoderFactory() {
            @Override public boolean supportsFormat(Format format) {
                return !(nativeAss && MimeTypes.TEXT_SSA.equals(format.sampleMimeType)) && SubtitleDecoderFactory.DEFAULT.supportsFormat(format);
            }
            @Override public androidx.media3.extractor.text.SubtitleDecoder createDecoder(Format format) {
                return SubtitleDecoderFactory.DEFAULT.createDecoder(format);
            }
        };
        return new TextRenderer(output, main.getLooper(), decoder);
    }

    private AssTextRenderer assRenderer(boolean secondary) {
        AssTextRenderer[] reference = new AssTextRenderer[1];
        AssTextRenderer renderer = new AssTextRenderer(() -> activeFonts, new AssTextRenderer.Output() {
            private final java.util.concurrent.atomic.AtomicReference<FrameUpdate> pending = new java.util.concurrent.atomic.AtomicReference<>();
            private final java.util.concurrent.atomic.AtomicBoolean posted = new java.util.concurrent.atomic.AtomicBoolean();
            @Override public void frame(AssFrame frame, int width, int height, int generation) {
                pending.set(new FrameUpdate(frame, width, height, generation));
                if (!posted.compareAndSet(false, true)) return;
                main.post(() -> {
                    posted.set(false);
                    FrameUpdate update = pending.getAndSet(null);
                    if (update == null || released || reference[0].generation() != update.generation) return;
                    if (secondary) { secondaryFrame = update.frame; secondaryWidth = update.width; secondaryHeight = update.height; }
                    else { primaryFrame = update.frame; primaryWidth = update.width; primaryHeight = update.height; }
                    AssOverlayView view = secondary ? secondaryAssView : primaryAssView;
                    if (view != null) view.frame(update.frame, update.width, update.height);
                });
            }
            @Override public void clear(int generation) { frame(null, 1, 1, generation); }
        });
        reference[0] = renderer;
        renderers.add(renderer);
        return renderer;
    }

    public void bind(PlayerView playerView) {
        if (host != null && playerView != null && host == playerView.getSubtitleView()) return;
        if (host != null) {
            host.removeView(secondaryView);
            if (primaryAssView != null) host.removeView(primaryAssView);
            if (secondaryAssView != null) host.removeView(secondaryAssView);
        }
        host = playerView == null ? null : playerView.getSubtitleView();
        secondaryView = null; primaryAssView = null; secondaryAssView = null;
        if (host == null) return;
        secondaryView = new SubtitleView(host.getContext());
        secondaryView.setApplyEmbeddedStyles(false);
        secondaryView.setBottomPaddingFraction(.20f);
        secondaryView.setCues(secondaryCues.cues);
        host.addView(secondaryView, new ViewGroup.LayoutParams(-1, -1));
        if (nativeAss) {
            primaryAssView = new AssOverlayView(host.getContext(), false);
            secondaryAssView = new AssOverlayView(host.getContext(), true);
            host.addView(primaryAssView, new ViewGroup.LayoutParams(-1, -1));
            host.addView(secondaryAssView, new ViewGroup.LayoutParams(-1, -1));
            primaryAssView.frame(primaryFrame, primaryWidth, primaryHeight);
            secondaryAssView.frame(secondaryFrame, secondaryWidth, secondaryHeight);
            primaryAssView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                for (AssTextRenderer renderer : renderers) renderer.size(r - l, b - t);
            });
        }
    }

    public void clear() {
        secondaryCues = CueGroup.EMPTY_TIME_ZERO;
        primaryFrame = secondaryFrame = null;
        if (secondaryView != null) secondaryView.setCues(List.of());
        if (primaryAssView != null) primaryAssView.frame(null, 1, 1);
        if (secondaryAssView != null) secondaryAssView.frame(null, 1, 1);
    }
    public void release() {
        released = true;
        bind(null);
        fontSets.clear();
        activeFonts = new SubtitleSource();
        primaryFrame = secondaryFrame = null;
        selectors.clear();
        renderers.clear();
    }
}
