package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

/** Keeps the first tap actionable on the new controls which also accept focus in touch mode. */
public final class TouchFocus {
    private TouchFocus() { }

    @SuppressLint("ClickableViewAccessibility") // Native onTouchEvent still owns performClick.
    public static void bind(View... views) {
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener((target, event) -> {
                // View.onTouchEvent skips its ACTION_UP click if it has to acquire focus then.
                // Acquire it at DOWN while leaving click, drag, long press and cancellation native.
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !target.isFocused()) target.requestFocus();
                return false;
            });
        }
    }
}
