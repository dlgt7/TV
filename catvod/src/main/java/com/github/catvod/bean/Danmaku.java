package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

public class Danmaku {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = TypeToken.getParameterized(List.class, (Type[])new Type[]{Danmaku.class}).getType();
    @SerializedName(value="name")
    private String name;
    @SerializedName(value="url")
    private String url;

    public static List<Danmaku> arrayFrom(String str) {
        return (List)GSON.fromJson(str, LIST_TYPE);
    }

    public static Danmaku create() {
        return new Danmaku();
    }

    public Danmaku name(String name) {
        this.name = name;
        return this;
    }

    public Danmaku url(String url) {
        this.url = url;
        return this;
    }
}
