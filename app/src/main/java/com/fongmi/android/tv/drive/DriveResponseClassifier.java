package com.fongmi.android.tv.drive;

import static com.fongmi.android.tv.drive.DriveCheckResult.Status.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Exact provider signals only. Auth, WAF, transport and unknown errors never mean dead shares. */
final class DriveResponseClassifier {

    private static final Set<String> ALI_BAD = new HashSet<>(Arrays.asList(
            "NotFound.ShareLink", "ShareLink.Cancelled", "ShareLink.Canceled", "ShareLink.Expired"));
    private static final Set<String> ALI_LOCKED = new HashSet<>(Arrays.asList(
            "ShareLinkPwdNeeded", "ShareLinkPwdInvalid", "InvalidParameter.ShareLinkPwd"));
    private static final Set<String> BAD_MESSAGES = new HashSet<>(Arrays.asList(
            "分享已取消", "分享链接已取消", "分享已失效", "分享链接已失效", "分享不存在", "分享已过期", "分享链接已过期"));
    private static final Set<String> LOCK_MESSAGES = new HashSet<>(Arrays.asList(
            "访问码错误", "访问码不正确", "请输入访问码", "提取码错误", "提取码不正确", "请输入提取码", "密码错误"));

    static DriveCheckResult.Status classify(String provider, int httpCode, String body) {
        if (httpCode != 200 && httpCode != 400 && httpCode != 404) return UNCERTAIN;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if ("夸克".equals(provider)) {
                String code = string(json, "code");
                if ("41008".equals(code)) return LOCKED;
                if ("41004".equals(code) || "41010".equals(code) || "41011".equals(code)) return BAD;
                if (httpCode == 200 && "0".equals(code) && !string(object(json, "data"), "stoken").isEmpty()) return OK;
            } else if ("阿里云盘".equals(provider)) {
                String code = string(json, "code");
                if (ALI_BAD.contains(code)) return BAD;
                if (ALI_LOCKED.contains(code)) return LOCKED;
                if (httpCode == 200 && code.isEmpty() && !string(json, "share_token").isEmpty()) return OK;
            } else if ("115".equals(provider)) {
                String error = string(json, "error");
                if (LOCK_MESSAGES.contains(error)) return LOCKED;
                if (BAD_MESSAGES.contains(error)) return BAD;
                if (httpCode != 200 || !"true".equals(string(json, "state"))) return UNCERTAIN;
                String errno = string(json, "errno");
                if (!errno.isEmpty() && !"0".equals(errno)) return UNCERTAIN;
                JsonObject data = object(json, "data");
                JsonObject info = object(data, "shareinfo");
                String reason = string(info, "forbid_reason");
                String state = string(data, "share_state");
                if (state.isEmpty()) state = string(info, "share_state");
                if ("7".equals(state) || BAD_MESSAGES.contains(reason)) return BAD;
                if (LOCK_MESSAGES.contains(reason)) return LOCKED;
                if (!reason.isEmpty()) return UNCERTAIN;
                if ("1".equals(state)) return OK;
                if (!state.isEmpty() && !"0".equals(state)) return UNCERTAIN;
                if (data.has("list") && data.get("list").isJsonArray() && data.getAsJsonArray("list").size() > 0) return OK;
            }
        } catch (RuntimeException malformed) {
            return UNCERTAIN;
        }
        return UNCERTAIN;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }
}
