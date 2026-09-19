package com.fongmi.android.tv.bean;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Json;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Danmaku {

    @SerializedName("name")
    private String name;
    @SerializedName("url")
    private String url;

    private transient boolean selected;

    public static List<Danmaku> arrayFrom(String str) {
        if (TextUtils.isEmpty(str)) return Collections.emptyList();
        str = str.trim();
        if (!Json.isArray(str) && !Json.isObj(str)) return List.of(Danmaku.from(str));
        Type listType = TypeToken.getParameterized(List.class, Danmaku.class).getType();
        if (Json.isObj(str)) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(str);
                String[] keys = {"data", "danmaku", "danmakus", "list", "result", "items", "rows"};
                for (String key : keys) {
                    if (obj.has(key) && obj.opt(key) instanceof org.json.JSONArray) {
                        List<Danmaku> items = App.gson().fromJson(obj.getJSONArray(key).toString(), listType);
                        return filterEmpty(items);
                    }
                }
            } catch (Throwable ignored) {}
            Danmaku item = App.gson().fromJson(str, Danmaku.class);
            return item == null || item.isEmpty() ? Collections.emptyList() : List.of(item);
        }
        return filterEmpty(App.gson().fromJson(str, listType));
    }

    private static List<Danmaku> filterEmpty(List<Danmaku> items) {
        if (items == null) return Collections.emptyList();
        List<Danmaku> result = new ArrayList<>();
        for (Danmaku item : items) if (item != null && !item.isEmpty()) result.add(item);
        return result;
    }

    public static Danmaku from(String url) {
        return from(url, url);
    }

    public static Danmaku from(String name, String url) {
        Danmaku danmaku = new Danmaku();
        danmaku.name = name;
        danmaku.url = url;
        return danmaku;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? getUrl() : name;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public Uri getUri() {
        return isEmpty() ? null : UrlUtil.uri(getUrl());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Danmaku it)) return false;
        return getUrl().equals(it.getUrl());
    }

    @Override
    public int hashCode() {
        return getUrl().hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
