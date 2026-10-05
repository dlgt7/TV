package com.fongmi.android.tv.ui.dialog;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogDanmakuSettingBinding;
import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Exercises the real panel and Android focus search without changing any danmaku settings. */
@RunWith(AndroidJUnit4.class)
public class DanmakuSettingFocusTest {
    private static final int[] TABS = {R.id.tabAppearance, R.id.tabTiming, R.id.tabDensity, R.id.tabDisplay};
    private static final int[] FIRST_CONTROLS = {R.id.textBoldSwitch, R.id.timeOffsetSlider, R.id.maxOnScreenSlider, R.id.showScrollSwitch};
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CorePlaybackActivity activity;
    private DialogDanmakuSettingBinding binding;

    @Before public void setup() {
        assumeTrue("The changed panel is TV-specific", "leanback".equals(BuildConfig.FLAVOR_mode));
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),
                CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            binding = DialogDanmakuSettingBinding.inflate(activity.getLayoutInflater());
            // Match the real playback side sheet, including its 24dp horizontal margins.
            int width = Math.round(320 * activity.getResources().getDisplayMetrics().density);
            activity.setContentView(binding.getRoot(), new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT));
            new DanmakuSettingPanel(binding, null).bind();
        });
        awaitFocus(TABS[0]);
        await(() -> binding.tabGroup.getWidth() > 0 && binding.tabGroup.getHeight() > 0, "laid out tab strip");
    }

    @After public void cleanup() {
        if (activity != null) main(activity::finish);
        instrumentation.waitForIdleSync();
    }

    @Test public void eachMaterialTabEntersItsOwnPageAndReturnsByDpad() throws IOException {
        assertEquals(MaterialButtonToggleGroup.class, binding.tabGroup.getClass());
        assertEquals(LinearLayout.HORIZONTAL, value(() -> binding.tabGroup.getOrientation()).intValue());
        int firstTop = value(() -> binding.tabAppearance.getTop());
        for (int index = 0; index < TABS.length; index++) {
            final int selected = index;
            awaitFocus(TABS[index]);
            for (int tabId : TABS) {
                await(() -> fullyVisible(activity.findViewById(tabId)), "all four tabs fully visible in the 320dp panel");
            }
            assertTrue(value(() -> ((MaterialButton) activity.findViewById(TABS[selected])).isChecked()));
            assertEquals(firstTop, value(() -> activity.findViewById(TABS[selected]).getTop()).intValue());
            assertTrue(value(() -> activity.findViewById(TABS[selected]).getBackground() instanceof RippleDrawable));
            assertOnlyPageFocusable(index);
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            awaitFocus(FIRST_CONTROLS[index]);
            captureTab(TABS[index]);
            press(KeyEvent.KEYCODE_DPAD_UP);
            awaitFocus(TABS[index]);
            press(KeyEvent.KEYCODE_DPAD_UP);
            awaitFocus(R.id.reset);
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            awaitFocus(TABS[index]);
            if (index < TABS.length - 1) press(KeyEvent.KEYCODE_DPAD_RIGHT);
        }
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        awaitFocus(TABS[3]);
        for (int index = TABS.length - 2; index >= 0; index--) {
            press(KeyEvent.KEYCODE_DPAD_LEFT);
            awaitFocus(TABS[index]);
        }
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        awaitFocus(TABS[0]);
    }

    @Test public void narrowTabStripScrollsAndLongPageDoesNotStealOtherTabsFocus() {
        main(() -> {
            ViewGroup.LayoutParams params = binding.getRoot().getLayoutParams();
            params.width = Math.round(280 * activity.getResources().getDisplayMetrics().density);
            binding.getRoot().setLayoutParams(params);
        });
        instrumentation.waitForIdleSync();
        assertTrue(binding.tabGroup.getParent() instanceof HorizontalScrollView);
        HorizontalScrollView strip = (HorizontalScrollView) binding.tabGroup.getParent();
        assertTrue("The narrow regression must exercise horizontal overflow", value(() -> binding.tabGroup.getWidth() > strip.getWidth()));
        for (int index = 1; index < TABS.length; index++) {
            press(KeyEvent.KEYCODE_DPAD_RIGHT);
            awaitFocus(TABS[index]);
            final int selected = index;
            await(() -> fullyVisible(activity.findViewById(TABS[selected])), "selected tab scrolled fully into view");
        }
        assertTrue("Focusing the last tab must scroll the strip", value(() -> strip.getScrollX() > 0));
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        awaitFocus(FIRST_CONTROLS[3]);
        press(KeyEvent.KEYCODE_DPAD_UP);
        awaitFocus(TABS[3]);
        for (int index = 2; index >= 0; index--) {
            press(KeyEvent.KEYCODE_DPAD_LEFT);
            awaitFocus(TABS[index]);
        }
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        awaitFocus(FIRST_CONTROLS[0]);
        // Reach the final appearance group with real keys, forcing the vertical content to scroll.
        for (int attempts = 0; attempts < 24 && !value(() -> binding.appearance.colorChipGroup.hasFocus()); attempts++) {
            press(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        assertTrue("Appearance controls below the fold must remain reachable", value(() -> binding.appearance.colorChipGroup.hasFocus()));
        assertTrue("The category strip stays visible while its content scrolls", value(() -> fullyVisible(binding.tabAppearance)));
        for (int attempts = 0; attempts < 24 && !value(() -> binding.tabAppearance.hasFocus()); attempts++) press(KeyEvent.KEYCODE_DPAD_UP);
        awaitFocus(TABS[0]);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        awaitFocus(TABS[1]);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        awaitFocus(FIRST_CONTROLS[1]);
        assertOnlyPageFocusable(1);
    }

    private void assertOnlyPageFocusable(int selected) {
        View[] pages = {binding.appearance.getRoot(), binding.timing.getRoot(), binding.density.getRoot(), binding.display.getRoot()};
        for (int index = 0; index < pages.length; index++) {
            View page = pages[index];
            assertEquals(index == selected ? View.VISIBLE : View.GONE, value(page::getVisibility).intValue());
            if (index != selected) {
                assertFalse(value(page::hasFocus));
                assertFalse("Hidden settings page is still a focus candidate", value(() -> {
                    for (View candidate : binding.getRoot().getFocusables(View.FOCUS_DOWN)) {
                        for (ViewParent parent = candidate.getParent(); parent != null; parent = parent.getParent()) {
                            if (parent == page) return true;
                        }
                    }
                    return false;
                }));
            }
        }
    }

    private boolean fullyVisible(View view) {
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.width() == view.getWidth() && rect.height() == view.getHeight();
    }

    private void captureTab(int tabId) throws IOException {
        SystemClock.sleep(250);
        instrumentation.waitForIdleSync();
        File external = instrumentation.getTargetContext().getExternalFilesDir(null);
        assertNotNull("External files directory is unavailable for focus screenshots", external);
        File directory = new File(external, "danmaku-focus");
        assertTrue("Could not create focus screenshot directory", directory.isDirectory() || directory.mkdirs());
        String name = activity.getResources().getResourceEntryName(tabId) + "-first-control.png";
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Could not capture the device-rendered danmaku panel", screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            assertTrue("Could not encode focus screenshot " + name, screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            screenshot.recycle();
        }
    }

    private void press(int keyCode) {
        instrumentation.sendKeyDownUpSync(keyCode);
        instrumentation.waitForIdleSync();
    }

    private void awaitFocus(int id) { await(() -> activity.findViewById(id).isFocused(), "focus on " + activity.getResources().getResourceEntryName(id)); }

    private void await(BooleanSupplier predicate, String reason) {
        long deadline = SystemClock.elapsedRealtime() + 5_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (value(predicate::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for " + reason + "; actual focus=" + value(activity::getCurrentFocus));
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
