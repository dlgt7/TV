package com.fongmi.android.tv.ui;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.test.CorePlaybackActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Real attached title widgets; no source loading, preference changes, or hidden API access. */
@RunWith(AndroidJUnit4.class)
public class PosterTitleMarqueeTest {
    private static final String LONG_TITLE = "A very long movie title that cannot possibly fit in this poster card and must scroll to reveal its ending";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CorePlaybackActivity activity;
    private LinearLayout host;
    private Button other;
    private View card;
    private TextView title;

    @Before public void setup() {
        assertEquals("Disposable sourceprobe only", "com.fongmi.android.tv.sourceprobe",
                instrumentation.getTargetContext().getPackageName());
        assumeTrue("Poster titles are TV-specific", "leanback".equals(BuildConfig.FLAVOR_mode));
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            host = new LinearLayout(activity);
            host.setOrientation(LinearLayout.VERTICAL);
            host.setPadding(dp(32), dp(32), dp(32), dp(32));
            other = new Button(activity);
            other.setText("Other focus target");
            other.setFocusableInTouchMode(true);
            host.addView(other, new LinearLayout.LayoutParams(dp(280), dp(48)));
            activity.setContentView(host);
            assertTrue(other.requestFocus());
        });
        await(() -> other.hasWindowFocus(), "activity window focus");
    }

    @After public void cleanup() {
        if (activity != null) main(activity::finish);
        instrumentation.waitForIdleSync();
    }

    @Test public void truncatedPosterActuallyScrollsAndBlurRestoresEllipsis() {
        attach(R.layout.adapter_vod, LONG_TITLE);
        assertEquals(TextUtils.TruncateAt.END, value(title::getEllipsize));
        main(() -> assertTrue(card.requestFocus()));
        awaitMarquee();
        assertPixelsMove();
        assertTrue(value(card::isFocused));
        assertFalse("The title must not steal focus from its card", value(title::isFocused));

        main(() -> assertTrue(other.requestFocus()));
        await(() -> !title.isSelected() && title.getEllipsize() == TextUtils.TruncateAt.END,
                "original ellipsis after blur");
        assertPixelsStayStill();
    }

    @Test public void focusedRebindStopsLongTitleAndDetachedCardDoesNotStealFocus() {
        attach(R.layout.adapter_vod, LONG_TITLE);
        main(() -> assertTrue(card.requestFocus()));
        awaitMarquee();
        assertPixelsMove();
        main(() -> title.setText("Short"));
        await(() -> !title.isSelected() && title.getEllipsize() == TextUtils.TruncateAt.END,
                "short rebound title restored while card stays focused");
        assertTrue(value(card::isFocused));
        assertPixelsStayStill();

        main(() -> title.setText(LONG_TITLE));
        awaitMarquee();
        main(() -> {
            host.removeView(card);
            assertFalse(title.isSelected());
            assertEquals(TextUtils.TruncateAt.END, title.getEllipsize());
            assertTrue(other.requestFocus());
            host.addView(card);
        });
        await(() -> title.isAttachedToWindow() && !title.isLayoutRequested(), "reattached title layout");
        assertTrue(value(other::isFocused));
        assertFalse(value(title::isSelected));
        assertPixelsStayStill();
        main(() -> assertTrue(card.requestFocus()));
        awaitMarquee();
        assertPixelsMove();
    }

    @Test public void historyTitleFittingTwoLinesStaysStillAndOverflowRestoresTwoLinesOnBlur() {
        // Explicit line break makes this independent of density and font-specific word wrapping.
        attach(R.layout.adapter_history, "First\nSecond");
        main(() -> assertTrue(card.requestFocus()));
        await(() -> title.getLayout() != null && title.getLayout().getLineCount() == 2
                && !title.isSelected() && !title.isLayoutRequested(),
                "original two-line history title");
        assertEquals(2, value(title::getMaxLines).intValue());
        assertFalse(value(title::isSelected));
        assertEquals(TextUtils.TruncateAt.END, value(title::getEllipsize));
        assertPixelsStayStill();

        main(() -> title.setText(LONG_TITLE));
        awaitMarquee();
        assertEquals(1, value(title::getMaxLines).intValue());
        assertPixelsMove();
        main(() -> assertTrue(other.requestFocus()));
        await(() -> title.getMaxLines() == 2 && !title.isSelected()
                && title.getEllipsize() == TextUtils.TruncateAt.END, "two-line history style restored");
        assertPixelsStayStill();
    }

    private void attach(int layout, String text) {
        main(() -> {
            card = activity.getLayoutInflater().inflate(layout, host, false);
            ViewGroup.LayoutParams params = card.getLayoutParams();
            params.width = dp(layout == R.layout.adapter_history ? 280 : 180);
            card.setLayoutParams(params);
            title = card.findViewById(R.id.name);
            title.setText(text);
            host.addView(card);
        });
        await(() -> title.getWidth() > 0 && title.getHeight() > 0 && !title.isLayoutRequested(), "attached title layout");
    }

    private void awaitMarquee() {
        await(() -> title.isSelected() && title.getEllipsize() == TextUtils.TruncateAt.MARQUEE
                && title.getLayout() != null && title.getLayout().getLineCount() == 1
                && !title.isLayoutRequested(), "single-line native marquee configuration");
    }

    private void assertPixelsMove() {
        // Draw only the actual TextView. Parent focus scale/ripple animation cannot produce a pass.
        // Native marquee translates text in TextView.onDraw; getScrollX() does not expose that offset.
        SystemClock.sleep(600);
        Bitmap first = pixels();
        try {
            long deadline = SystemClock.elapsedRealtime() + 6_000;
            while (SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(200);
                Bitmap next = pixels();
                try {
                    assertEquals(first.getWidth(), next.getWidth());
                    assertEquals(first.getHeight(), next.getHeight());
                    if (!first.sameAs(next)) return;
                } finally { next.recycle(); }
            }
            fail("Marquee flags were set but the actual title pixels never moved");
        } finally { first.recycle(); }
    }

    private void assertPixelsStayStill() {
        SystemClock.sleep(600);
        Bitmap first = pixels();
        try {
            // Longer than the platform's initial marquee delay; catches unwanted delayed starts.
            for (int sample = 0; sample < 8; sample++) {
                SystemClock.sleep(250);
                Bitmap next = pixels();
                try { assertTrue("A fitting, blurred, or unfocused title moved", first.sameAs(next)); }
                finally { next.recycle(); }
            }
        } finally { first.recycle(); }
    }

    private Bitmap pixels() {
        return value(() -> {
            Bitmap bitmap = Bitmap.createBitmap(title.getWidth(), title.getHeight(), Bitmap.Config.ARGB_8888);
            title.draw(new Canvas(bitmap));
            return bitmap;
        });
    }

    private int dp(int size) { return Math.round(size * activity.getResources().getDisplayMetrics().density); }

    private void await(BooleanSupplier condition, String description) {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (value(condition::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for " + description);
    }

    private void main(Runnable action) { value(() -> { action.run(); return null; }); }

    private <T> T value(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            try { result.set(action.get()); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("UI operation failed", failure.get());
        return result.get();
    }
}
