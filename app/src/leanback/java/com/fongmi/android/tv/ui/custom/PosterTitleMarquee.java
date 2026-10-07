package com.fongmi.android.tv.ui.custom;

import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TransformationMethod;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/** Enables the platform marquee only when a focused card's original title is truncated. */
public final class PosterTitleMarquee implements View.OnAttachStateChangeListener,
        View.OnLayoutChangeListener, ViewTreeObserver.OnGlobalFocusChangeListener,
        ViewTreeObserver.OnPreDrawListener, TextWatcher {

    private final TextView title;
    private final TextUtils.TruncateAt ellipsize;
    private final TransformationMethod transformation;
    private final int minLines;
    private final int maxLines;
    private final int minHeight;
    private final int maxHeight;
    private final int repeatLimit;
    private final boolean singleLine;
    private ViewTreeObserver observer;
    private boolean marquee;
    private boolean pending;
    private boolean configuring;
    private int availableWidth = -1;

    public static void bind(TextView title) {
        new PosterTitleMarquee(title);
    }

    private PosterTitleMarquee(TextView title) {
        this.title = title;
        ellipsize = title.getEllipsize();
        transformation = title.getTransformationMethod();
        singleLine = transformation instanceof SingleLineTransformationMethod;
        minLines = title.getMinLines();
        maxLines = title.getMaxLines();
        minHeight = title.getMinHeight();
        maxHeight = title.getMaxHeight();
        repeatLimit = title.getMarqueeRepeatLimit();
        title.addOnAttachStateChangeListener(this);
        title.addOnLayoutChangeListener(this);
        title.addTextChangedListener(this);
        if (title.isAttachedToWindow()) onViewAttachedToWindow(title);
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        observer = title.getViewTreeObserver();
        observer.addOnGlobalFocusChangeListener(this);
        observer.addOnPreDrawListener(this);
        evaluateOriginalLayout();
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) {
            observer.removeOnGlobalFocusChangeListener(this);
            observer.removeOnPreDrawListener(this);
        }
        observer = null;
        pending = false;
        restore();
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        evaluateOriginalLayout();
    }

    private boolean cardHasFocus() {
        View current = title;
        while (true) {
            if (current.isFocusable()) return current.hasFocus();
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return false;
            current = (View) parent;
        }
    }

    private int availableWidth() {
        return title.getWidth() - title.getCompoundPaddingLeft() - title.getCompoundPaddingRight();
    }

    private void evaluateOriginalLayout() {
        if (configuring) return;
        restore();
        pending = observer != null && cardHasFocus();
        if (pending) {
            // A recycled title must be measured with its original wrapping before deciding.
            title.requestLayout();
            title.invalidate();
        }
    }

    private void restore() {
        configuring = true;
        title.setSelected(false);
        if (marquee) {
            marquee = false;
            title.setSingleLine(singleLine);
            if (minLines >= 0) title.setMinLines(minLines);
            else title.setMinHeight(minHeight);
            if (maxLines >= 0) title.setMaxLines(maxLines);
            else title.setMaxHeight(maxHeight);
            title.setTransformationMethod(transformation);
            title.setEllipsize(ellipsize);
            title.setMarqueeRepeatLimit(repeatLimit);
        }
        configuring = false;
    }

    @Override
    public boolean onPreDraw() {
        if (!pending || title.isLayoutRequested()) return true;
        Layout layout = title.getLayout();
        if (layout == null || availableWidth() <= 0) return true;
        pending = false;
        availableWidth = availableWidth();
        if (!cardHasFocus() || !isTruncated(layout)) return true;
        configuring = true;
        marquee = true;
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        title.setMarqueeRepeatLimit(-1);
        title.setSelected(true);
        configuring = false;
        return true;
    }

    private boolean isTruncated(Layout layout) {
        if (maxLines > 0 && layout.getLineCount() > maxLines) return true;
        for (int line = 0; line < layout.getLineCount(); line++) {
            if (layout.getEllipsisCount(line) > 0) return true;
            if (layout.getLineCount() == 1 && layout.getLineWidth(line) > availableWidth) return true;
        }
        return false;
    }

    @Override
    public void onLayoutChange(View view, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int width = availableWidth();
        if (width == availableWidth) return;
        availableWidth = width;
        evaluateOriginalLayout();
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable text) {
        evaluateOriginalLayout();
    }
}
