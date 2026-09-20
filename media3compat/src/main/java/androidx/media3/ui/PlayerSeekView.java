package androidx.media3.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.media3.common.util.RepeatModeUtil;

/**
 * Seek-only Media3 controller embedded in the app playback overlay.
 *
 * <p>The app draws its own pause/play chrome in {@code view_control_*}. This view is reduced to a
 * timeline/scrubber so Media3 does not paint a second pause button, center transport controls, or
 * the full-screen controller scrim on top of the custom overlay.
 */
public final class PlayerSeekView extends PlayerControlView {

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setShowPreviousButton(false);
        setShowNextButton(false);
        setShowRewindButton(false);
        setShowFastForwardButton(false);
        setShowShuffleButton(false);
        setShowSubtitleButton(false);
        setShowVrButton(false);
        setShowPlayButtonIfPlaybackIsSuppressed(false);
        setRepeatToggleModes(RepeatModeUtil.REPEAT_TOGGLE_MODE_NONE);
        setShowTimeoutMs(0);
        hideTransportChrome();
    }

    private void hideTransportChrome() {
        int[] ids = {
                R.id.exo_play_pause,
                R.id.exo_center_controls,
                R.id.exo_controls_background,
                R.id.exo_prev,
                R.id.exo_next,
                R.id.exo_rew,
                R.id.exo_ffwd,
                R.id.exo_settings,
                R.id.exo_fullscreen,
                R.id.exo_minimal_fullscreen,
                R.id.exo_overflow_show,
                R.id.exo_overflow_hide,
                R.id.exo_basic_controls,
                R.id.exo_minimal_controls,
                R.id.exo_extra_controls_scroll_view,
                R.id.exo_vr,
                R.id.exo_shuffle,
                R.id.exo_repeat_toggle,
                R.id.exo_subtitle,
        };
        for (int id : ids) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(GONE);
        }
        // Keep timeline scrubbing; do not steal clicks from the custom overlay chrome.
        setClickable(false);
        setFocusable(false);
        setFocusableInTouchMode(false);
    }

    public TimeBar getTimeBar() {
        TimeBar timeBar = findViewById(R.id.exo_progress);
        if (timeBar == null) throw new IllegalStateException("Player control layout has no time bar");
        return timeBar;
    }
}
