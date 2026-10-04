package com.fongmi.android.tv.player.subtitle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.fongmi.android.tv.setting.PlayerSetting;

import io.github.peerless2012.ass.AssFrame;
import io.github.peerless2012.ass.AssTex;

/** Display-only overlay. libass calculates frames on the renderer's worker thread. */
final class AssOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private final boolean secondary;
    private AssFrame frame;
    private int frameWidth = 1;
    private int frameHeight = 1;

    AssOverlayView(Context context, boolean secondary) {
        super(context);
        this.secondary = secondary;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void frame(AssFrame frame, int width, int height) {
        this.frame = frame;
        frameWidth = Math.max(1, width);
        frameHeight = Math.max(1, height);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (frame == null || frame.getImages() == null) return;
        float scaleX = getWidth() / (float) frameWidth;
        float scaleY = getHeight() / (float) frameHeight;
        float shift = getHeight() * (PlayerSetting.getSubtitlePosition() + (secondary ? .12f : 0));
        for (AssTex image : frame.getImages()) {
            if (image.getBitmap() == null) continue;
            int rgba = image.getColor();
            paint.setColor(((255 - (rgba & 255)) << 24) | ((rgba >>> 8) & 0xffffff));
            destination.set(image.getX() * scaleX, image.getY() * scaleY - shift,
                    (image.getX() + image.getW()) * scaleX, (image.getY() + image.getH()) * scaleY - shift);
            canvas.drawBitmap(image.getBitmap(), null, destination, paint);
        }
    }
}
