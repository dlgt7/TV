package androidx.media3.common;

import androidx.annotation.IntDef;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Dolby Vision 输出策略。
 */
public final class DolbyVisionOutputPolicy {

    /** 由设备能力自动决定。 */
    public static final int AUTO = 0;
    /** 强制要求 Dolby Vision 直通输出。 */
    public static final int REQUIRED = 1;
    /** 假定设备不支持，直接按普通 HDR 处理。 */
    public static final int ASSUME_UNSUPPORTED = 2;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUTO, REQUIRED, ASSUME_UNSUPPORTED})
    public @interface Mode {
    }

    public static boolean isValid(int policy) {
        return policy == AUTO || policy == REQUIRED || policy == ASSUME_UNSUPPORTED;
    }

    public static @Mode int parse(int policy) {
        return isValid(policy) ? policy : AUTO;
    }

    private DolbyVisionOutputPolicy() {
    }
}
