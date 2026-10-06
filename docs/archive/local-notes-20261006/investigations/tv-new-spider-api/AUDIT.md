# FongMi new crawler API audit (2026-10-06)

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../../README.md)。

## Verified upstream evidence

- https://api.github.com/repos/FongMi/TV reports only `fongmi`, default `fongmi`, HEAD c616c0aa3613e87529791587a9f71b78c278c991 (2026-09-28).
- `main` does not exist on remote; raw main Spider.java returns 404. `fongmi` catvod still exposes old Spider only. The commits query for catvod/.../net/Net.java returns []. Do not claim its current published source contains the new APIs.
- FongMi/CatVodSpider `main` HEAD 3d7a0fa1a6737603231f78fb7cbaea6539d9d6e3 publishes new docs, examples, and compiled SDK:
  https://github.com/FongMi/CatVodSpider/blob/main/docs/development.md
  https://github.com/FongMi/CatVodSpider/blob/main/app/libs/catvod-api.jar
- Downloaded SDK: catvod-api.jar. Exact public/private ABI: api-signatures.txt. Core bytecode: api-core-bytecode.txt. CFR decompilation for reference: sdk-decompiled/. Decompiled output is NOT original public Java source, may need generic/control-flow repair, and SDK lacks internal CacheOptions.class.

## Minimal Java implementation pieces

- New net/Net.java, NetOptions.java, NetCache.java, NetStore.java, and reconstructed package-private CacheOptions.java. Net contains Result, Cookies, SessionCookies, Exchange, Strict nested classes.
- Add utils/Local.java, Json.strict(String), Json.link(String, JsonObject), and Crypto.sha256 dependency (SDK full Crypto exposes wider API; avoid replacing existing per-language Crypto).
- Add bean/Result.java plus Class.java, Vod.java, Filter.java, Sub.java, Danmaku.java and their inner classes if full SDK helpers are promised.
- Append Spider fields `public Net net`, `public com.github.catvod.utils.Local local`, methods getActivity(), getProxyUrl(), getProxyUrl(Map<String,String>), preserve all current methods including static client(). SDK removed client(); copying it verbatim breaks old JAR ABI.
- Extend Init with setActivity(Supplier<Activity>), activity(), setToast(Consumer<String>), toast(String). Register App::activity and UI-thread toast consumer; activity filters null/finishing/destroyed.
- JarLoader injects net/local by siteKey BEFORE arbitrary subclass init. SDK base init does not set them; subclasses need not call super. Explicitly close Net on failure and loader destruction even if subclass overrides destroy without super.
- Shared Cookies implementation should bridge application WebView CookieManager; Net.sessions use separate in-memory cookies and cannot persist cache. Existing Gson 2.14/OkHttp 5.5/Java 21+desugar suffice; no network dependency migration required.
- Keep API names/members in R8 for dynamically loaded JARs and reflection. Existing rules keep Spider and Proxy, not the complete newly exposed surface.

## Public API groups

- Net: constructors (), (Cookies), (Cookies,String siteKey); session/close; getCookie/setCookie; get/get(headers)/get(timeout); post/post(headers); req/json/jsonText/ws (URL, JSON options); request/requestAsync/enqueue with NetOptions; socketAsync/webSocket; reqParts/wsParts/batchParts; batch(requestsJson[, limit]); download(url, optionsJson, File), downloadAsync; sleep; cached/cachedAsync/cachedValue/peek/refresh/clearCache([key]). See exact overloads in api-signatures.txt.
- Net.Result: public final Object code, Map<String,Object> headers, byte[] body, String error; failure(Throwable), json(NetOptions), parts(NetOptions), text(). Failure code is empty String, not a status integer. HTTP headers may have String or List values.
- NetOptions JSON: buffer(default 0), redirect(1), timeout(10000 ms), callTimeout(0), postType(json), method(get), body, data, headers, params, cookie(false). Text convenience methods use 15000 ms. body/data exclusive. Query arrays repeat key. buffer 1/3 produce byte array, 2 Base64. Net req returns response JSON; json throws on network error/non-2xx/invalid JSON.
- Local(String siteKey): get(key)->JSON String/null; set(key,valid JSON String), delete(key), clear(). SharedPreferences name spider_local_ + sha256(siteKey); clear HTTP cache must not delete it.
- Result.page(List<Vod>, int page,int limit): clamps page to >=1, rejects limit<1, slices safely with long arithmetic; pagecount >=1 even for empty data, reports total=0. (The docs validation Python stub uses different empty-pagecount semantics; bytecode is authoritative for Java.)
- Proxy helper: URL ?do=jar&siteKey=... with Uri query encoding; Map cannot contain reserved do/siteKey keys.
- Cache: required ttl>=0, stale default true, persist default false; combines in-flight same-key calls, stale-while-revalidate, per-site disk storage, cancellation. SDK NetCache caps 64 entries/16 MiB and NetStore similar. Tests should use deterministic clock/controlled futures.

## Network integration hazards (must repair)

1. Net.builder in SDK blindly follows native redirects. Existing OkHttp uses ProxyRedirectInterceptor with native redirects disabled to recalculate proxy per hop. Preserve it and native followRedirects(false); when options redirect=0 remove only ProxyRedirectInterceptor, as OkHttp.noRedirect already does. Do not replace OkHttp.java.
2. SDK socketClient replaces socket factory/hostname verifier with Strict.CLIENT and therefore strips custom ECH. Preserve current ECH/proxy policy. WS disabled redirects must also remove ProxyRedirectInterceptor, not merely set followRedirects(false).
3. Net close must cancel only its own tracked calls/WebSockets/cache loads/sessions, not OkHttp global dispatcher or connections used by playback.
4. Adding host helpers changes parent-first lookup for old JAR bundled versions. Watch bean.Result/Vod/etc and utils.Crypto classes: old custom methods can become NoSuchMethodError. Consider selective child-first for these newly hosted helpers only, host fallback when absent. Never broad child-first for Spider, OkHttp, existing bean.Doh/Header/Proxy, or field-typed Net/Local, due identity/loader constraints.

## JS/Python integration and backward compatibility

- JS add net object + global page/link/getProxyUrl and site Local. Keep legacy req/http/getProxy/js2Proxy and Local get(rule,key)/set(rule,key,value)/delete(rule,key). A dispatcher can select old/new Local arity.
- New JS default factory accepts site object (at least key), current wrapper calls default() with no argument.
- New JS data methods return object/array or Promise thereof; current Java Spider casts results to String. Stringify resolved ordinary data objects on the JS executor while retaining legacy strings. Do not alter proxy/boolean results. All callbacks touching QuickJS must return to its executor; blocking the same executor waiting for its callback deadlocks.
- Net.http/json/batch/ws/cached/download/sleep Promise APIs, req synchronous by default, refresh immediate. cached loader may return value or Promise. Handle close/rejection and release JS refs.
- Python inject self.net/self.local before init, wrap JSON to native dict/list/scalars, support buffer=3 bytes, session context manager. Add page/link/getActivity/getProxyUrl(dict). Preserve old getProxyUrl(local=True) and bool positional behavior, fetch/post/legacy cache helpers.
- BaseLoader.proxy already routes siteKey directly to matching spider; no need to replace old recent-JAR proxy fallback.

## Suggested meaningful checks

- Compile representative new SDK JAR and old ABI JAR against current runtime; call inherited client/safeDns plus old init without super, legacy proxy and JS local forms.
- Local same/different site identity, HTTP cache/local clear separation; caching TTL/stale/concurrent load/cancel/persist with session rejection.
- HTTP params/form/body/buffer, HTTP failure vs JSON rejection, redirects disabled and cross-proxy redirect, WebSocket ECH inheritance, per-owner cancellation isolation.
- Device/instrumented tests for real Dex loading, R8 release API retention, QuickJS object/Promise/callback execution, Python bridge. Host stub tests alone do not establish these behaviors.
