package com.github.catvod.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import java.util.HashMap;
import java.util.Map;

public class NetOptions {
    private static final Gson GSON = new Gson();
    @SerializedName("buffer")
    private Integer buffer;
    @SerializedName("redirect")
    private Integer redirect;
    @SerializedName("timeout")
    private Integer timeout;
    @SerializedName("callTimeout")
    private Integer callTimeout;
    @SerializedName("postType")
    private String postType;
    @SerializedName("method")
    private String method;
    @SerializedName("body")
    private String body;
    @SerializedName("data")
    private JsonElement data;
    @SerializedName("headers")
    private JsonElement headers;
    @SerializedName("params")
    private JsonElement params;
    @SerializedName("cookie")
    private Boolean cookie;

    public static NetOptions from(String json) {
        NetOptions options = json == null || json.isEmpty() ? null : (NetOptions)GSON.fromJson(json, NetOptions.class);
        return options == null ? new NetOptions() : options;
    }

    static NetOptions text(String method, String body, Map<String, String> headers, int timeout) {
        JsonObject values;
        NetOptions options = new NetOptions();
        values = headers == null ? new JsonObject() : GSON.toJsonTree(headers).getAsJsonObject();
        if (method.equals("POST") && values.keySet().stream().noneMatch(key -> key.equalsIgnoreCase("Content-Type"))) {
            values.addProperty("Content-Type", "application/json; charset=utf-8");
        }
        options.method = method;
        options.body = body;
        options.headers = values;
        options.timeout = timeout;
        return options;
    }

    public int getBuffer() {
        return this.buffer == null ? 0 : this.buffer;
    }

    public int getRedirect() {
        return this.redirect == null ? 1 : this.redirect;
    }

    public int getTimeout() {
        return this.timeout == null ? 10000 : this.timeout;
    }

    public int getCallTimeout() {
        if (this.callTimeout != null && this.callTimeout < 0) {
            throw new IllegalArgumentException("callTimeout must not be negative");
        }
        return this.callTimeout == null ? 0 : this.callTimeout;
    }

    public String getPostType() {
        return this.postType == null || this.postType.isEmpty() ? "json" : this.postType;
    }

    public String getMethod() {
        return this.method == null || this.method.isEmpty() ? "get" : this.method;
    }

    public String getBody() {
        return this.body;
    }

    public JsonElement getData() {
        return this.data;
    }

    public JsonElement getParams() {
        return this.params;
    }

    public boolean isRedirect() {
        return this.getRedirect() == 1;
    }

    public boolean isCookie() {
        return Boolean.TRUE.equals(this.cookie);
    }

    public Map<String, String> getHeader() {
        return NetOptions.toMap(this.headers);
    }

    static Map<String, String> toMap(JsonElement element) {
        JsonObject object;
        HashMap<String, String> values = new HashMap<>();
        try {
            if (element != null && element.isJsonPrimitive()) {
                element = JsonParser.parseString((String)element.getAsString());
            }
            object = element.getAsJsonObject();
        }
        catch (RuntimeException e) {
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                values.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsJsonPrimitive().getAsString().trim());
            }
            catch (RuntimeException e) {
                values.put((String)entry.getKey(), "");
            }
        }
        return values;
    }

    public String getCharset() {
        for (Map.Entry<String, String> entry : this.getHeader().entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("Content-Type")) continue;
            for (String part : entry.getValue().split(";")) {
                String text = part.trim();
                if (!text.startsWith("charset=")) continue;
                return text.substring(8).replace("\"", "");
            }
        }
        return "UTF-8";
    }
}
