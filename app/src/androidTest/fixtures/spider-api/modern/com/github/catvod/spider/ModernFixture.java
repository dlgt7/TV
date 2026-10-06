package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import org.json.JSONObject;
import java.util.Collections;

public final class ModernFixture extends Spider {
    private boolean bound;
    private String endpoint;

    @Override public void init(Context context, String extend) {
        // No super.init: the host must bind resources before entering this method.
        bound = net != null && local != null;
        if (!bound) throw new IllegalStateException("Runtime was not bound before init");
        local.set("fixture", "{\"bound\":true}");
        endpoint = extend;
    }

    @Override public String homeContent(boolean filter) throws Exception {
        String page = Result.page(Collections.singletonList(new Vod("modern", "fixture", "")), 1, 20).string();
        return new JSONObject(page).put("bound", bound)
                .put("http", new JSONObject(net.json(endpoint, "{}")).getBoolean("ok"))
                .put("hostResult", Result.class.getClassLoader() == Spider.class.getClassLoader())
                .put("local", new JSONObject(local.get("fixture")).getBoolean("bound")).toString();
    }
}
