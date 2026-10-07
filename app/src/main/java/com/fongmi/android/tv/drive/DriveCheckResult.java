package com.fongmi.android.tv.drive;

public final class DriveCheckResult {

    public enum Status { OK, BAD, LOCKED, UNSUPPORTED, UNCERTAIN }

    public final Status status;
    public final String provider;
    public final String message;
    /** Share URL only: never includes a password, access token, fragment or unrelated query. */
    public final String normalizedUrl;

    DriveCheckResult(Status status, String provider, String normalizedUrl) {
        this.status = status;
        this.provider = provider;
        this.normalizedUrl = normalizedUrl;
        switch (status) {
            case OK: message = "分享链接可访问，可继续尝试播放"; break;
            case BAD: message = "平台确认分享已失效或取消"; break;
            case LOCKED: message = "需要提取码，或提取码不正确"; break;
            case UNSUPPORTED: message = "暂不支持检测此平台，可继续尝试播放"; break;
            default: message = "暂时无法确认，请继续用原来源尝试播放";
        }
    }
}
