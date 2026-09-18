package androidx.media3.ui.danmaku;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.ui.PlayerView;
import okhttp3.OkHttpClient;

public final class DanmakuPlayerViewController {

    public DanmakuPlayerViewController() {}

    public void bind(PlayerView playerView) {}

    public void setOkHttpClient(OkHttpClient client) {}

    public void setEnabled(boolean enabled) {}

    public void setConfig(DanmakuConfig config) {}

    public void setDataSource(@Nullable Uri uri) {}

    public void sendNow(String text) {}

    public void close() {}
}