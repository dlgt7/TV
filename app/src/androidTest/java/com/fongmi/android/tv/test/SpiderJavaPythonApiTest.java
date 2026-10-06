package com.fongmi.android.tv.test;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderRuntime;
import com.github.catvod.net.Net;
import com.github.catvod.utils.Local;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

/** Real Android storage, HTTP and Chaquopy bridge checks; no user configuration is loaded. */
@RunWith(AndroidJUnit4.class)
public final class SpiderJavaPythonApiTest {
    private final Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void javaResourcesAreBoundBeforeInitAndOwnedBySite() throws Exception {
        String key = "api-test-" + UUID.randomUUID();
        Spider first = new Spider() {
            @Override public void init(Context context, String extend) {
                assertNotNull(net);
                assertNotNull(local);
                local.set("init", "{\"bound\":true}");
            }
        };
        Spider second = new Spider() { };
        Fixture server = new Fixture();
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        try {
            SpiderRuntime.bind(first, key);
            first.init(target, "");
            SpiderRuntime.bind(second, key + "-other");
            assertEquals("{\"bound\":true}", new Local(key).get("init"));
            assertNull(second.local.get("init"));
            assertSame(Spider.client(), com.github.catvod.net.OkHttp.client());
            Uri proxy = Uri.parse(first.getProxyUrl(Map.of("url", "https://example.test/?a=中&b=2")));
            assertEquals(key, proxy.getQueryParameter("siteKey"));
            assertEquals("https://example.test/?a=中&b=2", proxy.getQueryParameter("url"));
            assertEquals("jar", proxy.getQueryParameter("do"));
            try { first.getProxyUrl(Map.of("do", "wrong")); fail("reserved parameter accepted"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(new JSONObject(first.net.json(server.url("/json"), "{}")).getBoolean("ok"));
            assertEquals("{\"cached\":1}", first.net.cached("http", "{\"ttl\":1000}", () -> "{\"cached\":1}"));
            first.net.clearCache();
            assertNotNull(first.local.get("init"));
            Net session = first.net.session();
            session.setCookie(server.url("/"), "only=session");
            assertTrue(session.getCookie(server.url("/")).contains("only=session"));
            session.close();
            SpiderRuntime.close(first);
            assertTrue(new JSONObject(first.net.req(server.url("/json"), "{}")).has("error"));
            assertTrue(new JSONObject(second.net.json(server.url("/json"), "{}")).getBoolean("ok"));
            JSONObject page = new JSONObject(Result.page(List.of(new Vod("1", "one", ""),
                    new Vod("2", "two", ""), new Vod("3", "three", "")), 2, 2).toString());
            assertEquals(3, page.getInt("total"));
            assertEquals("3", page.getJSONArray("list").getJSONObject(0).getString("vod_id"));
        } finally {
            if (first.local != null) first.local.clear();
            if (second.local != null) second.local.clear();
            SpiderRuntime.close(first);
            SpiderRuntime.close(second);
            server.stop();
        }
    }

    @Test public void pythonRunsNativeAdaptersAndLegacyRequestsInTheRealVm() throws Exception {
        new com.fongmi.chaquo.Loader();
        Python python = Python.getInstance();
        PyObject module = python.getModule("types").callAttr("ModuleType", "spider_api_device_test");
        python.getBuiltins().callAttr("exec", PYTHON_SOURCE, module.get("__dict__"));
        PyObject object = module.callAttr("Probe");
        com.fongmi.chaquo.Spider spider = new com.fongmi.chaquo.Spider(python.getModule("app"), object, "probe.py");
        spider.siteKey = "python-api-test-" + UUID.randomUUID();
        Fixture server = new Fixture();
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        try {
            spider.init(target, server.url(""));
            JSONObject result = new JSONObject(spider.homeContent(true));
            assertTrue(result.getBoolean("native"));
            assertTrue(result.getBoolean("legacy"));
            assertTrue(result.getBoolean("binary"));
            assertTrue(result.getBoolean("cache"));
            assertEquals("#EXTM3U\nfixture", spider.liveContent(""));
            assertEquals(200, spider.proxy(Map.of())[0]);
        } finally {
            if (spider.local != null) spider.local.clear();
            spider.destroy();
            server.stop();
        }
    }

    private static final class Fixture extends NanoHTTPD {
        Fixture() { super("127.0.0.1", 0); }
        String url(String path) { return "http://127.0.0.1:" + getListeningPort() + path; }
        @Override public Response serve(IHTTPSession request) {
            if (request.getUri().equals("/bytes"))
                return newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
                        new ByteArrayInputStream(new byte[]{0, 127, (byte) 128, (byte) 255}), 4);
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true,\"items\":[1,false,null]}");
        }
    }

    private static final String PYTHON_SOURCE = """
            from base.spider import Spider
            from urllib.parse import urlsplit, parse_qs
            class Probe(Spider):
                def init(self, extend=''):
                    assert self.net is not None and self.local is not None
                    self.url = extend
                    self.local.set('bound', {'ok': True})
                def homeContent(self, filter):
                    native = self.net.json(self.url + '/json')
                    assert native == {'ok': True, 'items': [1, False, None]}
                    assert self.local.get('bound') == {'ok': True}
                    raw = self.net.req(self.url + '/bytes', {'buffer': 3})['content']
                    assert raw == bytes([0,127,128,255])
                    batch = self.net.batch([{'url': self.url + '/json'}, {'url': self.url + '/bytes', 'options': {'buffer': 3}}])
                    assert batch[0]['code'] == 200 and batch[1]['content'] == raw
                    value = self.net.cached('key', {'ttl': 10000}, lambda: {'x': [1, False, None]})
                    assert self.net.peek('key') == value
                    self.net.clearCache()
                    assert self.local.get('bound') == {'ok': True}
                    with self.net.session() as session:
                        assert session.json(self.url + '/json')['ok']
                    legacy = self.fetch(self.url + '/json', params={'page': 2}, timeout=5)
                    assert legacy.status_code == 200 and legacy.json()['ok']
                    assert self.post(self.url + '/json', data={'name': 'legacy'}, timeout=5).json()['ok']
                    assert self.getProxyUrl(True).endswith('?do=py')
                    assert self.getProxyUrl(False).endswith('?do=py')
                    query = parse_qs(urlsplit(self.getProxyUrl({'url':'https://example.test/?a=1&b=中'})).query)
                    assert query['siteKey'] == [self.siteKey] and query['url'] == ['https://example.test/?a=1&b=中']
                    assert self.page([1,2,3], 2, 2)['list'] == [3]
                    return {'native': True, 'legacy': True, 'binary': True, 'cache': True}
                def liveContent(self, url):
                    return '#EXTM3U\\nfixture'
                def localProxy(self, params):
                    return [200, 'application/octet-stream', bytes([0,128,255])]
            """;
}
