package com.fongmi.android.tv.event;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.EventBus;

public class ErrorEvent {

    private final Type type;
    private String msg;

    public static void url() {
        EventBus.getDefault().post(new ErrorEvent(Type.URL));
    }

    public static void drm() {
        EventBus.getDefault().post(new ErrorEvent(Type.DRM));
    }

    public static void flag() {
        EventBus.getDefault().post(new ErrorEvent(Type.FLAG));
    }

    public static void parse() {
        EventBus.getDefault().post(new ErrorEvent(Type.PARSE));
    }

    public static void timeout() {
        EventBus.getDefault().post(new ErrorEvent(Type.TIMEOUT));
    }

    public static void extract(String msg) {
        EventBus.getDefault().post(new ErrorEvent(Type.EXTRACT, msg));
    }

    public ErrorEvent(Type type) {
        this.type = type;
    }

    public ErrorEvent(Type type, String msg) {
        this.msg = msg;
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public String getMsg() {
        if (type == Type.URL) return ResUtil.getString(R.string.error_play_url);
        if (type == Type.DRM) return ResUtil.getString(R.string.error_play_drm_scheme);
        if (type == Type.FLAG) return ResUtil.getString(R.string.error_play_flag);
        if (type == Type.PARSE) return ResUtil.getString(R.string.error_play_parse);
        if (type == Type.TIMEOUT) return ResUtil.getString(R.string.error_play_timeout);
        return msg;
    }

    public enum Type {
        URL, DRM, FLAG, PARSE, TIMEOUT, EXTRACT
    }
}
