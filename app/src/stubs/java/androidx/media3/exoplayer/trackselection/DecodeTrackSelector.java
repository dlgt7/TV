package androidx.media3.exoplayer.trackselection;

import android.content.Context;
import androidx.media3.exoplayer.ExoPlayer;

public class DecodeTrackSelector extends DefaultTrackSelector {

    public DecodeTrackSelector(Context context) {
        super(context);
    }

    public void setRendererDecodePreferences(int audioDecode, int videoDecode) {}
}