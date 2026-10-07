package com.fongmi.android.tv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.VerticalGridView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.FeaturedVodRow;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.custom.JetStreamPageProgressLayout;
import com.fongmi.android.tv.ui.presenter.VodPresenter;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Exercises the real View/Compose/Leanback focus boundary using inert poster fixtures. */
@RunWith(AndroidJUnit4.class)
public final class HomeNavigationIntegrationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private HomeActivity activity;
    private VerticalGridView recycler;
    private View nav;

    @Test(timeout = 45000)
    public void posterUpReturnsToNavigationWithoutSkippingOtherRows() throws Exception {
        assertEquals("Disposable sourceprobe only", "com.fongmi.android.tv.sourceprobe",
                instrumentation.getTargetContext().getPackageName());
        try {
            activity = (HomeActivity) instrumentation.startActivitySync(new Intent(
                    instrumentation.getTargetContext(), HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("Home window focus", () -> activity.hasWindowFocus());
            instrumentation.runOnMainSync(() -> {
                // Do not let an asynchronous source response replace this transient fixture.
                ((SiteViewModel) field("mViewModel")).getResult().removeObservers(activity);
                recycler = activity.findViewById(R.id.recycler);
                nav = activity.findViewById(R.id.nav);
                ((JetStreamPageProgressLayout) activity.findViewById(R.id.progressLayout)).showContent();
                ArrayObjectAdapter adapter = (ArrayObjectAdapter) field("mAdapter");
                adapter.clear();
                Vod poster = Vod.objectFrom("{\"vod_id\":\"focus-fixture\",\"vod_name\":\"Focus fixture\"}");
                adapter.add(R.string.home_recommend);
                adapter.add(FeaturedVodRow.create(Collections.singletonList(poster)));
                adapter.add(R.string.home_recommend);
                ArrayObjectAdapter row = new ArrayObjectAdapter(new VodPresenter(activity));
                row.add(poster);
                adapter.add(new ListRow(row));
                invokeFocus(1);
            });
            await("Hero focused", () -> recycler.hasFocus() && recycler.getSelectedPosition() == 1);
            key(KeyEvent.KEYCODE_DPAD_UP);
            await("UP from first poster must enter top navigation", nav::hasFocus);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            await("DOWN from navigation must restore first poster", () -> recycler.hasFocus()
                    && recycler.getSelectedPosition() == 1);

            instrumentation.runOnMainSync(() -> invokeFocus(3));
            await("Lower poster row focused", () -> recycler.hasFocus() && recycler.getSelectedPosition() == 3);
            key(KeyEvent.KEYCODE_DPAD_UP);
            await("UP from lower row must visit hero before navigation", () -> recycler.hasFocus()
                    && recycler.getSelectedPosition() == 1 && !nav.hasFocus());
            key(KeyEvent.KEYCODE_DPAD_UP);
            await("Second UP must enter navigation", nav::hasFocus);
        } finally {
            if (activity != null) instrumentation.runOnMainSync(activity::finish);
        }
    }

    private Object field(String name) {
        try {
            Field field = HomeActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private void invokeFocus(int position) {
        try {
            Method method = HomeActivity.class.getDeclaredMethod("requestRecyclerFocus", int.class);
            method.setAccessible(true);
            method.invoke(activity, position);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private void key(int code) {
        instrumentation.sendKeyDownUpSync(code);
    }

    private void await(String message, BooleanSupplier predicate) {
        long deadline = SystemClock.uptimeMillis() + 7000;
        AtomicBoolean result = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> result.set(predicate.getAsBoolean()));
            if (result.get()) return;
            SystemClock.sleep(50);
        }
        assertTrue(message, result.get());
    }
}
