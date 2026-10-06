package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

/** A cached, alpha-shaped rim for title artwork, without tinting its original colors. */
public final class TmdbLogoTransformation extends BitmapTransformation {

    private static final byte[] ID = "com.fongmi.android.tv.utils.TmdbLogoTransformation.v1".getBytes(Key.CHARSET);
    private final float density;

    public TmdbLogoTransformation(float density) {
        this.density = Math.max(1f, density);
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap source, int outWidth, int outHeight) {
        int padding = padding(density);
        // The view reserves 3 dp on each side in addition to its original content height.
        // Fit into that content area first; adding the rim must not shrink the title.
        Bitmap fitted = TransformationUtils.fitCenter(pool, source, Math.max(1, outWidth - 2 * padding), Math.max(1, outHeight - 2 * padding));
        try {
            int width = fitted.getWidth();
            int height = fitted.getHeight();
            int[] pixels = new int[width * height];
            fitted.getPixels(pixels, 0, width, 0, 0, width, height);
            int resultWidth = width + 2 * padding;
            int resultHeight = height + 2 * padding;
            Bitmap result = pool.get(resultWidth, resultHeight, Bitmap.Config.ARGB_8888);
            result.setHasAlpha(true);
            result.setDensity(source.getDensity());
            result.setPixels(render(pixels, width, height, density), 0, resultWidth, 0, 0, resultWidth, resultHeight);
            return result;
        } finally {
            if (fitted != source) pool.put(fitted);
        }
    }

    static boolean hasTransparentEdge(int[] pixels, int width, int height) {
        for (int x = 0; x < width; x++) {
            if ((pixels[x] >>> 24) < 16 || (pixels[(height - 1) * width + x] >>> 24) < 16) return true;
        }
        for (int y = 0; y < height; y++) {
            if ((pixels[y * width] >>> 24) < 16 || (pixels[y * width + width - 1] >>> 24) < 16) return true;
        }
        return false;
    }

    static int padding(float density) {
        return Math.round(3f * density);
    }

    /** Runs once in Glide's decode worker; only the completed bitmap is drawn by the UI. */
    static int[] render(int[] pixels, int width, int height, float density) {
        int padding = padding(density);
        int w = width + padding * 2;
        int h = height + padding * 2;
        // An opaque fallback still gets layout padding, but never a rectangular rim.
        if (!hasTransparentEdge(pixels, width, height)) {
            int[] result = new int[w * h];
            for (int y = 0; y < height; y++) System.arraycopy(pixels, y * width, result, (y + padding) * w + padding, width);
            return result;
        }
        int[] alpha = new int[w * h];
        int[] light = new int[alpha.length];
        int[] dark = new int[alpha.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = pixels[y * width + x];
                int index = (y + padding) * w + x + padding;
                int a = color >>> 24;
                // A dark letter gets a pale rim; a pale letter gets a dark rim.
                // The smooth blend also works for multicolored title artwork.
                float luminance = ((color >> 16 & 255) * 0.2126f + (color >> 8 & 255) * 0.7152f + (color & 255) * 0.0722f) / 255f;
                float pale = Math.max(0f, Math.min(1f, (0.65f - luminance) / 0.4f));
                alpha[index] = a;
                light[index] = Math.round(a * pale);
                dark[index] = Math.round(a * (1f - pale));
            }
        }
        float radius = 0.65f * density;
        int extent = (int) Math.ceil(radius + 0.5f);
        int[] rim = new int[alpha.length];
        // A max filter preserves the original alpha softness without making a thick halo.
        for (int dy = -extent; dy <= extent; dy++) {
            for (int dx = -extent; dx <= extent; dx++) {
                float weight = Math.min(1f, radius + 0.5f - (float) Math.hypot(dx, dy));
                if (weight <= 0) continue;
                for (int y = Math.max(0, -dy); y < Math.min(h, h - dy); y++) {
                    for (int x = Math.max(0, -dx); x < Math.min(w, w - dx); x++) {
                        int at = y * w + x;
                        int from = at + dy * w + dx;
                        int l = Math.max(rim[at] >>> 16, Math.round(light[from] * weight));
                        int d = Math.max(rim[at] & 255, Math.round(dark[from] * weight));
                        rim[at] = l << 16 | d;
                    }
                }
            }
        }
        // Two small box passes approximate a soft shadow, including inside letter holes.
        // The transparent padding covers its entire support, so no edge is clipped.
        int blur = Math.max(1, Math.round(density));
        blur(alpha, light, w, h, blur, true);
        blur(light, dark, w, h, blur, false);
        blur(dark, light, w, h, blur, true);
        blur(light, alpha, w, h, blur, false);
        int shadowOffset = Math.max(1, Math.round(density * 0.5f));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                int edge = rim[index];
                int shadow = y >= shadowOffset ? Math.round(alpha[index - shadowOffset * w] * 0.30f) << 24 : 0;
                int paleRim = Math.round((edge >>> 16) * 0.46f) << 24 | 0xE9EBF0;
                int darkRim = Math.round((edge & 255) * 0.60f) << 24 | 0x080A0F;
                int below = over(darkRim, over(paleRim, shadow));
                int original = x >= padding && x < padding + width && y >= padding && y < padding + height
                        ? pixels[(y - padding) * width + x - padding] : 0;
                rim[index] = over(original, below);
            }
        }
        return rim;
    }

    private static void blur(int[] input, int[] output, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width;
        int length = horizontal ? width : height;
        int step = horizontal ? 1 : width;
        int diameter = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int start = horizontal ? line * width : line;
            int sum = 0;
            for (int i = 0; i <= radius && i < length; i++) sum += input[start + i * step];
            for (int i = 0; i < length; i++) {
                output[start + i * step] = sum / diameter;
                if (i - radius >= 0) sum -= input[start + (i - radius) * step];
                if (i + radius + 1 < length) sum += input[start + (i + radius + 1) * step];
            }
        }
    }

    private static int over(int foreground, int background) {
        int a = foreground >>> 24;
        if (a == 255) return foreground;
        if (a == 0) return background;
        int b = (background >>> 24) * (255 - a) / 255;
        int alpha = a + b;
        int red = ((foreground >> 16 & 255) * a + (background >> 16 & 255) * b) / alpha;
        int green = ((foreground >> 8 & 255) * a + (background >> 8 & 255) * b) / alpha;
        int blue = ((foreground & 255) * a + (background & 255) * b) / alpha;
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID);
        messageDigest.update(ByteBuffer.allocate(4).putFloat(density).array());
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof TmdbLogoTransformation && Float.compare(density, ((TmdbLogoTransformation) object).density) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * TmdbLogoTransformation.class.getName().hashCode() + Float.floatToIntBits(density);
    }
}
