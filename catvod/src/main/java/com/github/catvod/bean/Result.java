package com.github.catvod.bean;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Danmaku;
import com.github.catvod.bean.Filter;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public class Result {
    private static final Gson GSON = new Gson();
    private static final Gson OUTPUT = GSON.newBuilder().disableHtmlEscaping().create();
    private static final Type FILTERS_TYPE = TypeToken.getParameterized(LinkedHashMap.class, (Type[])new Type[]{String.class, TypeToken.getParameterized(List.class, (Type[])new Type[]{Filter.class}).getType()}).getType();
    @SerializedName(value="class")
    private List<Class> classes;
    @SerializedName(value="list")
    private List<Vod> list;
    @SerializedName(value="filters")
    private LinkedHashMap<String, List<Filter>> filters;
    @SerializedName(value="header")
    private String header;
    @SerializedName(value="format")
    private String format;
    @SerializedName(value="danmaku")
    private List<Danmaku> danmaku;
    @SerializedName(value="click")
    private String click;
    @SerializedName(value="msg")
    private String msg;
    @SerializedName(value="url")
    private Object url;
    @SerializedName(value="subs")
    private List<Sub> subs;
    @SerializedName(value="parse")
    private int parse;
    @SerializedName(value="jx")
    private int jx;
    @SerializedName(value="page")
    private Integer page;
    @SerializedName(value="pagecount")
    private Integer pagecount;
    @SerializedName(value="limit")
    private Integer limit;
    @SerializedName(value="total")
    private Integer total;

    public static Result objectFrom(String str) {
        return (Result)GSON.fromJson(str, Result.class);
    }

    public static String string(List<Class> classes, List<Vod> list, LinkedHashMap<String, List<Filter>> filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JSONObject filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list, JsonElement filters) {
        return Result.get().classes(classes).vod(list).filters(filters).string();
    }

    public static String string(List<Class> classes, LinkedHashMap<String, List<Filter>> filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, JsonElement filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, JSONObject filters) {
        return Result.get().classes(classes).filters(filters).string();
    }

    public static String string(List<Class> classes, List<Vod> list) {
        return Result.get().classes(classes).vod(list).string();
    }

    public static String string(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        if (list.get(0) instanceof Vod) {
            return Result.get().vod((List<Vod>) (List<?>) list).string();
        }
        if (list.get(0) instanceof Class) {
            return Result.get().classes((List<Class>) (List<?>) list).string();
        }
        return "";
    }

    public static String string(Vod item) {
        return Result.get().vod(item).string();
    }

    public static String error(String msg) {
        return Result.get().vod(Collections.emptyList()).msg(msg).string();
    }

    public static String notify(String msg) {
        return Result.get().msg(msg).string();
    }

    public static Result get() {
        return new Result();
    }

    public static Result page(List<Vod> items, int page, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        page = Math.max(1, page);
        int total = items.size();
        int start = (int)Math.min((long)total, (long)(page - 1) * (long)limit);
        int end = (int)Math.min((long)total, (long)start + (long)limit);
        int count = Math.max(1, (int)(((long)total + (long)limit - 1L) / (long)limit));
        Result result = Result.get().vod(items.subList(start, end)).page(page, count, limit, total);
        result.total = total;
        return result;
    }

    public Result classes(List<Class> classes) {
        this.classes = classes;
        return this;
    }

    public Result vod(List<Vod> list) {
        this.list = list;
        return this;
    }

    public Result vod(Vod item) {
        this.list = Arrays.asList(item);
        return this;
    }

    public Result filters(LinkedHashMap<String, List<Filter>> filters) {
        this.filters = filters;
        return this;
    }

    public Result filters(JSONObject object) {
        return object == null ? this : this.filters(JsonParser.parseString((String)object.toString()));
    }

    public Result filters(JsonElement element) {
        if (element == null) {
            return this;
        }
        this.filters = (LinkedHashMap)GSON.fromJson(element, FILTERS_TYPE);
        return this;
    }

    public Result header(Map<String, String> header) {
        if (header.isEmpty()) {
            return this;
        }
        this.header = GSON.toJson(header);
        return this;
    }

    public Result chrome() {
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36");
        this.header(header);
        return this;
    }

    public Result parse() {
        this.parse = 1;
        return this;
    }

    public Result parse(int parse) {
        this.parse = parse;
        return this;
    }

    public Result jx() {
        this.jx = 1;
        return this;
    }

    public Result url(String url) {
        this.url = url;
        return this;
    }

    public Result url(List<String> url) {
        this.url = url;
        return this;
    }

    public Result danmaku(List<Danmaku> danmaku) {
        this.danmaku = danmaku;
        return this;
    }

    public Result click(String click) {
        this.click = click;
        return this;
    }

    public Result msg(String msg) {
        this.msg = msg;
        return this;
    }

    public Result format(String format) {
        this.format = format;
        return this;
    }

    public Result subs(List<Sub> subs) {
        this.subs = subs;
        return this;
    }

    public Result dash() {
        this.format = "application/dash+xml";
        return this;
    }

    public Result m3u8() {
        this.format = "application/x-mpegURL";
        return this;
    }

    public Result rtsp() {
        this.format = "application/x-rtsp";
        return this;
    }

    public Result octet() {
        this.format = "application/octet-stream";
        return this;
    }

    public Result page() {
        return this.page(1, 1, 0, 1);
    }

    public Result page(int page, int count, int limit, int total) {
        this.page = page > 0 ? page : Integer.MAX_VALUE;
        this.limit = limit > 0 ? limit : Integer.MAX_VALUE;
        this.total = total > 0 ? total : Integer.MAX_VALUE;
        this.pagecount = count > 0 ? count : Integer.MAX_VALUE;
        return this;
    }

    public List<Vod> getList() {
        return this.list == null ? Collections.emptyList() : this.list;
    }

    public String string() {
        return this.toString();
    }

    public String toString() {
        return OUTPUT.toJson((Object)this);
    }
}
