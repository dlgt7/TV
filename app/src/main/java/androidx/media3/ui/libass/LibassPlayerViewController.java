package androidx.media3.ui.libass;

import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.libass.LibassPlaybackSession;
import androidx.media3.exoplayer.libass.LibassSubtitleController;
import androidx.media3.exoplayer.text.SecondaryTextOutput;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;

import java.util.List;
import java.util.function.Consumer;

/**
 * 把副字幕渲染到 {@link PlayerView} 上的第二个 {@link SubtitleView}，并同步主字幕的样式/缩放。
 */
public final class LibassPlayerViewController implements LibassPlaybackSession.StyleListener {

    private final Handler handler;
    private final LibassPlaybackSession session;
    private final LibassSubtitleController controller;
    private final SecondaryTextOutput.Listener cueListener = this::onSecondaryCues;

    @Nullable
    private final SecondaryTextOutput secondaryTextOutput;

    @Nullable
    private PlayerView playerView;
    @Nullable
    private SubtitleView secondaryView;
    @Nullable
    private Consumer<SubtitleView> configurator;

    public LibassPlayerViewController(ExoPlayer player, LibassPlaybackSession session, LibassSubtitleController controller) {
        this.handler = new Handler(Looper.getMainLooper());
        this.session = session;
        this.controller = controller;
        this.secondaryTextOutput = controller == null ? null : controller.secondaryTextOutput();
    }

    public void bind(@Nullable PlayerView playerView) {
        if (this.playerView == playerView) return;
        unbind();
        if (playerView == null) return;
        this.playerView = playerView;
        this.secondaryView = createSubtitleView(playerView);
        playerView.addView(secondaryView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        session.setStyleListener(this);
        if (secondaryTextOutput != null) secondaryTextOutput.addListener(cueListener);
        applyStyle();
    }

    public void setStyleOverride(@Nullable CaptionStyleCompat style, @Nullable String fontFamily) {
        session.setStyleOverride(style, fontFamily);
        applyStyle();
    }

    public void setSecondarySubtitleViewConfigurator(@Nullable Consumer<SubtitleView> configurator) {
        this.configurator = configurator;
        applyStyle();
    }

    public void close() {
        unbind();
        session.close();
    }

    @Override
    public void onSubtitleStyleChanged() {
        applyStyle();
    }

    private void unbind() {
        session.setStyleListener(null);
        if (secondaryTextOutput != null) secondaryTextOutput.removeListener(cueListener);
        if (secondaryView != null && playerView != null) playerView.removeView(secondaryView);
        secondaryView = null;
        playerView = null;
    }

    private void onSecondaryCues(@NonNull CueGroup cueGroup) {
        List<Cue> cues = cueGroup.cues;
        handler.post(() -> {
            if (secondaryView == null) return;
            secondaryView.setCues(cues);
        });
    }

    private void applyStyle() {
        handler.post(this::applyStyleInternal);
    }

    private void applyStyleInternal() {
        if (controller != null && controller.isClosed()) return;
        applySecondaryStyle();
        applyPrimaryStyle();
    }

    private void applySecondaryStyle() {
        SubtitleView view = secondaryView;
        if (view == null) return;
        applyScale(view);
        if (secondaryTextOutput != null) view.setCues(secondaryTextOutput.getCueGroup().cues);
        if (configurator != null) configurator.accept(view);
    }

    private void applyPrimaryStyle() {
        PlayerView view = playerView;
        if (view == null) return;
        SubtitleView subtitleView = view.getSubtitleView();
        if (subtitleView == null) return;
        applyScale(subtitleView);
        float padding = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION + session.getBottomPositionFraction();
        subtitleView.setBottomPaddingFraction(Math.min(1.0f, Math.max(0.0f, padding)));
        CaptionStyleCompat style = session.getStyleOverride();
        if (style != null) {
            subtitleView.setStyle(style);
            subtitleView.setApplyEmbeddedStyles(false);
        } else {
            subtitleView.setApplyEmbeddedStyles(true);
        }
    }

    private void applyScale(SubtitleView view) {
        float scale = session.getFontScale();
        if (scale <= 0.0f) scale = 1.0f;
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale, false);
    }

    private static SubtitleView createSubtitleView(PlayerView playerView) {
        SubtitleView subtitleView = new SubtitleView(playerView.getContext());
        subtitleView.setUserDefaultStyle();
        subtitleView.setUserDefaultTextSize();
        subtitleView.setApplyEmbeddedStyles(true);
        subtitleView.setApplyEmbeddedFontSizes(true);
        return subtitleView;
    }
}
