package androidx.media3.exoplayer.text;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.text.CueGroup;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 第二字幕输出。播放器中额外注册的一个 {@link TextRenderer} 会把第二条字幕轨的字幕送到这里，
 * 再由 {@link androidx.media3.ui.libass.LibassPlayerViewController} 转发到第二个 {@link androidx.media3.ui.SubtitleView}。
 */
public final class SecondaryTextOutput implements TextOutput {

    public interface Listener {

        void onSecondaryCues(@NonNull CueGroup cueGroup);
    }

    private final CopyOnWriteArrayList<Listener> listeners;

    private CueGroup cueGroup;

    public SecondaryTextOutput() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.cueGroup = CueGroup.EMPTY_TIME_ZERO;
    }

    @Override
    public void onCues(@NonNull CueGroup cueGroup) {
        this.cueGroup = cueGroup;
        for (Listener listener : listeners) listener.onSecondaryCues(cueGroup);
    }

    @Override
    public void onCues(@NonNull List<androidx.media3.common.text.Cue> cues) {
        onCues(new CueGroup(cues, cueGroup.presentationTimeUs));
    }

    public void addListener(@NonNull Listener listener) {
        listeners.add(listener);
        listener.onSecondaryCues(cueGroup);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    public void clearListeners() {
        listeners.clear();
    }

    @NonNull
    public CueGroup getCueGroup() {
        return cueGroup;
    }

    public void reset() {
        cueGroup = CueGroup.EMPTY_TIME_ZERO;
        for (Listener listener : listeners) listener.onSecondaryCues(cueGroup);
    }

    @Nullable
    public static SecondaryTextOutput cast(@Nullable Object output) {
        return output instanceof SecondaryTextOutput ? (SecondaryTextOutput) output : null;
    }
}
