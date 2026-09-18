package androidx.media3.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;

public class PlayerSeekView extends FrameLayout {

    public PlayerSeekView(Context context) {
        super(context);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public PlayerSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setPlayer(@Nullable Player player) {}

    @Nullable
    public TimeBar getTimeBar() {
        return null;
    }
}