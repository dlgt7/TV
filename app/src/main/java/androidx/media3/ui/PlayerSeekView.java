package androidx.media3.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;

/**
 * 只保留进度条的轻量播放控制视图：内部持有一个 {@link DefaultTimeBar}，
 * 负责与 {@link Player} 双向同步（播放进度 / 缓冲进度 / 时长 / 拖拽定位）。
 */
public class PlayerSeekView extends FrameLayout {

    private final DefaultTimeBar timeBar;
    private final ComponentListener componentListener;
    private final Runnable updateProgressAction;
    private final Handler handler;

    @Nullable
    private Player player;

    private boolean scrubbing;
    private long duration;

    public PlayerSeekView(Context context) {
        this(context, null);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.handler = new Handler(Looper.getMainLooper());
        this.updateProgressAction = this::updateProgress;
        this.componentListener = new ComponentListener();
        this.duration = C.TIME_UNSET;
        this.timeBar = new DefaultTimeBar(context, attrs);
        this.timeBar.setId(androidx.media3.ui.R.id.exo_progress);
        this.timeBar.addListener(componentListener);
        addView(timeBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setPlayer(@Nullable Player player) {
        if (this.player == player) return;
        if (this.player != null) this.player.removeListener(componentListener);
        this.player = player;
        if (player != null) player.addListener(componentListener);
        updateAll();
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    @Nullable
    public TimeBar getTimeBar() {
        return timeBar;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(updateProgressAction);
    }

    private void updateAll() {
        updateTimeline();
        updateProgress();
        updateEnabled();
    }

    private void updateTimeline() {
        Player player = this.player;
        if (player == null) {
            duration = C.TIME_UNSET;
            timeBar.setDuration(C.TIME_UNSET);
            return;
        }
        Timeline timeline = player.getCurrentTimeline();
        if (timeline.isEmpty()) {
            duration = C.TIME_UNSET;
        } else {
            Timeline.Window window = timeline.getWindow(player.getCurrentMediaItemIndex(), new Timeline.Window());
            long windowDuration = window.getDurationMs();
            duration = windowDuration == C.TIME_UNSET ? player.getDuration() : windowDuration;
        }
        timeBar.setDuration(duration);
        timeBar.setAdGroupTimesMs(null, null, 0);
    }

    private void updateProgress() {
        Player player = this.player;
        if (player == null) return;
        if (!scrubbing) {
            timeBar.setPosition(player.getCurrentPosition());
            timeBar.setBufferedPosition(player.getBufferedPosition());
        }
        handler.removeCallbacks(updateProgressAction);
        int state = player.getPlaybackState();
        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
            handler.postDelayed(updateProgressAction, Math.max(16L, timeBar.getPreferredUpdateDelay()));
        }
    }

    private void updateEnabled() {
        Player player = this.player;
        boolean enabled = player != null && player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && player.isCurrentMediaItemSeekable();
        timeBar.setEnabled(enabled);
    }

    private void seekTo(long positionMs) {
        Player player = this.player;
        if (player == null || !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return;
        if (duration != C.TIME_UNSET) positionMs = Math.min(positionMs, duration);
        player.seekTo(Math.max(0, positionMs));
    }

    private final class ComponentListener implements Player.Listener, TimeBar.OnScrubListener {

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            updateProgress();
            updateEnabled();
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            updateProgress();
        }

        @Override
        public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
            updateTimeline();
            updateProgress();
            updateEnabled();
        }

        @Override
        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
            updateTimeline();
            updateProgress();
        }

        @Override
        public void onScrubStart(@NonNull TimeBar timeBar, long position) {
            scrubbing = true;
        }

        @Override
        public void onScrubMove(@NonNull TimeBar timeBar, long position) {
            timeBar.setPosition(position);
        }

        @Override
        public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
            scrubbing = false;
            if (!canceled) seekTo(position);
            updateProgress();
        }
    }
}
