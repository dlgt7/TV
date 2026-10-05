package com.fongmi.android.tv.player.exo;

import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager;
import androidx.media3.exoplayer.source.preload.PreloadException;
import androidx.media3.exoplayer.source.preload.PreloadManagerListener;

import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;

import java.lang.reflect.Field;

/**
 * Observes the production engine without consuming, replacing or preparing its sources.
 * Create before {@link ExoPlayerEngine#preload}, then poll {@link #isCompleted()} before
 * calling {@link ExoPlayerEngine#start}. All methods, including close, require the main thread.
 * One probe watches one preload request; create a new probe for a retry or another item.
 */
@UnstableApi
public final class SourcePreloadProbe implements AutoCloseable {
    private static final Field ENGINE_NEXT = field(ExoPlayerEngine.class, "next");
    private static final Field MANAGER = field(NextMediaPreload.class, "manager");
    private static final Field PENDING = field(NextMediaPreload.class, "pending");
    private static final Field ADDED = field(NextMediaPreload.class, "added");
    private static final Field CONSUMED = field(NextMediaPreload.class, "consumed");
    private static final Field RELEASED = field(NextMediaPreload.class, "released");

    private final ExoPlayerEngine engine;
    private final NextMediaPreload next;
    private final DefaultPreloadManager manager;
    private final MediaItem expected;
    private final PreloadManagerListener listener;
    private MediaSource completedSource;
    private PreloadException error;
    private boolean closed;

    public SourcePreloadProbe(ExoPlayerEngine engine, PlaySpec expected) {
        requireMainThread();
        this.engine = engine;
        this.expected = MediaItemFactory.from(expected);
        next = (NextMediaPreload) read(ENGINE_NEXT, engine);
        manager = (DefaultPreloadManager) read(MANAGER, next);
        listener = new PreloadManagerListener() {
            @Override public void onCompleted(MediaItem item) {
                requireMainThread();
                if (!closed && matches(item)) completedSource = manager.getMediaSource(item);
            }

            @Override public void onError(PreloadException exception) {
                requireMainThread();
                if (!closed && matches(exception.mediaItem)) error = exception;
            }
        };
        manager.addListener(listener);
    }

    /** A non-null manager source alone does not establish that preloading completed. */
    public boolean isCompleted() {
        requireActive();
        return error == null && completedSource != null && getMediaSource() == completedSource;
    }

    public PreloadException getError() {
        requireActive();
        return error;
    }

    /** Returns only the source for this request, using the engine's exact pending MediaItem. */
    public MediaSource getMediaSource() {
        requireActive();
        MediaItem pending = (MediaItem) read(PENDING, next);
        return matches(pending) ? manager.getMediaSource(pending) : null;
    }

    /** Observes the flag written by the production take() path; never calls take() itself. */
    public boolean isConsumed() {
        requireActive();
        return matches((MediaItem) read(PENDING, next)) && (Boolean) read(CONSUMED, next);
    }

    /** Call immediately after engine.start(expected, position), before clearing the preload. */
    public void assertConsumedPreparedSource() {
        requireActive();
        if (!isCompleted() || !isConsumed()
                || !matches(engine.getPlayer().getCurrentMediaItem())) {
            throw new AssertionError("Engine did not consume the completed next source: " + describeState());
        }
    }

    /** Contains no media URLs, item IDs or request headers. */
    public String describeState() {
        requireActive();
        return "completed=" + (completedSource != null)
                + ", pendingMatches=" + matches((MediaItem) read(PENDING, next))
                + ", added=" + read(ADDED, next)
                + ", consumed=" + read(CONSUMED, next)
                + ", sameSource=" + (completedSource != null && getMediaSource() == completedSource)
                + ", error=" + (error != null);
    }

    /** Detaches only this listener. The caller owns engine and preference cleanup. */
    @Override public void close() {
        requireMainThread();
        if (closed) return;
        manager.removeListener(listener);
        closed = true;
    }

    private boolean matches(MediaItem item) {
        return item != null && NextMediaPreload.sameMedia(expected, item);
    }

    private void requireActive() {
        requireMainThread();
        if (closed || read(ENGINE_NEXT, engine) != next || (Boolean) read(RELEASED, next)) {
            throw new IllegalStateException("Preload probe is closed or its engine was rebuilt/released");
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("SourcePreloadProbe must run on the main thread");
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Production preload field is unavailable: " + name, error);
        }
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("Cannot observe production preload state", error);
        }
    }
}
