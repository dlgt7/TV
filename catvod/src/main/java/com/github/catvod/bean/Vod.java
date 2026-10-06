package com.github.catvod.bean;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

public class Vod {
    private static final Gson GSON = new Gson();
    @SerializedName(value="type_name")
    private String typeName;
    @SerializedName(value="vod_id")
    private String vodId;
    @SerializedName(value="vod_name")
    private String vodName;
    @SerializedName(value="vod_pic")
    private String vodPic;
    @SerializedName(value="vod_remarks")
    private String vodRemarks;
    @SerializedName(value="vod_year")
    private String vodYear;
    @SerializedName(value="vod_area")
    private String vodArea;
    @SerializedName(value="vod_actor")
    private String vodActor;
    @SerializedName(value="vod_director")
    private String vodDirector;
    @SerializedName(value="vod_content")
    private String vodContent;
    @SerializedName(value="vod_play_from")
    private String vodPlayFrom;
    @SerializedName(value="vod_play_url")
    private String vodPlayUrl;
    @SerializedName(value="vod_tag")
    private String vodTag;
    @SerializedName(value="action")
    private String action;
    @SerializedName(value="style")
    private Style style;

    public static Vod objectFrom(String str) {
        Vod item = (Vod)GSON.fromJson(str, Vod.class);
        return item == null ? new Vod() : item;
    }

    public static Vod action(String action) {
        Vod vod = new Vod();
        vod.action = action;
        return vod;
    }

    public Vod() {
    }

    public Vod(String vodId, String vodName, String vodPic) {
        this.setVodId(vodId);
        this.setVodName(vodName);
        this.setVodPic(vodPic);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks) {
        this(vodId, vodName, vodPic);
        this.setVodRemarks(vodRemarks);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, String action) {
        this(vodId, vodName, vodPic, vodRemarks);
        this.setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style) {
        this(vodId, vodName, vodPic, vodRemarks);
        this.setStyle(style);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, Style style, String action) {
        this(vodId, vodName, vodPic, vodRemarks, style);
        this.setAction(action);
    }

    public Vod(String vodId, String vodName, String vodPic, String vodRemarks, boolean folder) {
        this(vodId, vodName, vodPic, vodRemarks);
        this.setVodTag(folder ? "folder" : "file");
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public void setVodId(String vodId) {
        this.vodId = vodId;
    }

    public void setVodName(String vodName) {
        this.vodName = vodName;
    }

    public void setVodPic(String vodPic) {
        this.vodPic = vodPic;
    }

    public void setVodRemarks(String vodRemarks) {
        this.vodRemarks = vodRemarks;
    }

    public void setVodYear(String vodYear) {
        this.vodYear = vodYear;
    }

    public void setVodArea(String vodArea) {
        this.vodArea = vodArea;
    }

    public void setVodActor(String vodActor) {
        this.vodActor = vodActor;
    }

    public void setVodDirector(String vodDirector) {
        this.vodDirector = vodDirector;
    }

    public void setVodContent(String vodContent) {
        this.vodContent = vodContent;
    }

    public String getVodContent() {
        return this.vodContent;
    }

    public void setVodPlayFrom(String vodPlayFrom) {
        this.vodPlayFrom = vodPlayFrom;
    }

    public void setVodPlayUrl(String vodPlayUrl) {
        this.vodPlayUrl = vodPlayUrl;
    }

    public String getVodPlayUrl() {
        return this.vodPlayUrl;
    }

    public void setVodTag(String vodTag) {
        this.vodTag = vodTag;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public static class Style {
        @SerializedName(value="type")
        private String type;
        @SerializedName(value="ratio")
        private Float ratio;

        public static Style rect() {
            return Style.rect(0.75f);
        }

        public static Style rect(float ratio) {
            return new Style("rect", Float.valueOf(ratio));
        }

        public static Style oval() {
            return new Style("oval", Float.valueOf(1.0f));
        }

        public static Style full() {
            return new Style("full");
        }

        public static Style list() {
            return new Style("list");
        }

        public Style(String type) {
            this.type = type;
        }

        public Style(String type, Float ratio) {
            this(type);
            this.ratio = ratio;
        }
    }
}
