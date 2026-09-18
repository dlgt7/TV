package androidx.media3.common;

import androidx.annotation.IntDef;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class DolbyVisionOutputPolicy {

    public static final int AUTO = 0;
    public static final int REQUIRED = 1;
    public static final int ASSUME_UNSUPPORTED = 2;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUTO, REQUIRED, ASSUME_UNSUPPORTED})
    public @interface Mode {}

    private DolbyVisionOutputPolicy() {}
}