package com.github.catvod.spider;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import org.json.JSONObject;

public final class LegacyFixture extends Spider {
    @Override public String homeContent(boolean filter) throws Exception {
        return new JSONObject().put("result", Result.legacyMarker()).put("vod", Vod.legacyMarker())
                .put("client", client() != null)
                .put("hostSpider", Spider.class.getClassLoader() != getClass().getClassLoader())
                .put("hostNet", com.github.catvod.net.Net.class.getClassLoader() == Spider.class.getClassLoader())
                .toString();
    }
    @Override public void destroy() { throw new IllegalStateException("fixture destroy failure"); }
}
