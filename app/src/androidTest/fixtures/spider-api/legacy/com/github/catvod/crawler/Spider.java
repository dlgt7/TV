package com.github.catvod.crawler;

/** Must never win over the real host Spider during DEX loading. */
public abstract class Spider {
    public String siteKey;
    public static okhttp3.OkHttpClient client() { throw new AssertionError("Plugin Spider loaded"); }
    public void init(android.content.Context context, String extend) throws Exception { }
    public String homeContent(boolean filter) throws Exception { return ""; }
    public void destroy() { }
}
