package com.github.catvod.bean;

import com.google.gson.annotations.SerializedName;

public class Sub {
    @SerializedName(value="url")
    private String url;
    @SerializedName(value="name")
    private String name;
    @SerializedName(value="lang")
    private String lang;
    @SerializedName(value="format")
    private String format;
    @SerializedName(value="flag")
    private int flag;

    public static Sub create() {
        return new Sub();
    }

    public Sub name(String name) {
        this.name = name;
        return this;
    }

    public Sub url(String url) {
        this.url = url;
        return this;
    }

    public Sub lang(String lang) {
        this.lang = lang;
        return this;
    }

    private Sub format(String format) {
        this.format = format;
        return this;
    }

    public Sub forced() {
        this.flag = 2;
        return this;
    }

    public Sub ext(String ext) {
        switch (ext) {
            case "vtt": {
                return this.format("text/vtt");
            }
            case "ass":
            case "ssa": {
                return this.format("text/x-ssa");
            }
        }
        return this.format("application/x-subrip");
    }
}
