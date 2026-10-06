# Cloudflare preferred-domain routing: reviewed design

> 研究快照（2026-10-06）：保留分析过程，不作为完整构建或设备验收结论。该功能在归档时仍处于其他会话的开发中。

Reviewed actual OkHttp 5.5.0 source (`parent-5.5.0`) on 2026-10-06; cached source excerpts are beside this report. Production files have not been changed.

## Minimal robust placement

Install `CloudflarePreferredInterceptor` after `ProxyRedirectInterceptor`. The redirect interceptor invokes subsequent interceptors separately per redirect, supplying its inherited/selected `ProxySelector.Policy`. Do not change URLs, HTTP Host, headers, SNI, ECH config resolution, credentials or socket factories. OkHttp 5.5 exposes `Chain.withDns` publicly, so routing can change DNS only within one application-interceptor attempt and remain the same Call (preserving cancellation, tags, event listener and timeout).

Eligible requests: feature set to a valid hostname, original HTTPS port 443 hostname (not literal IP), normal host resolver with no explicit hosts mapping, and effective proxy list entirely DIRECT. Limit route changes to GET/HEAD with no request body; other methods go normally to avoid automatically submitting a request twice. This conservative exclusion also avoids a one-shot body being consumed across fallback.

Resolve original host normally first. Every returned A/AAAA address must belong to the already checked-in Cloudflare ranges; unknown, mixed answers and DNS failures skip routing. Resolve preferred hostname through the same selected DNS provider, bypassing only the new route feature itself; require every returned address to be CF. This classification must be independent of ECH being enabled. Preserve hosts override precedence, including wildcard/regex mappings.

Route only the original hostname to a bounded preferred address list (one IPv4 and one IPv6, or at most two total); all other DNS queries delegate to normal DNS. Do not append origin IPs to this first preferred attempt, because that obscures whether a bad HTTP response came from the preferred edge.

## Actual OkHttp retry coverage

* Its fast fallback races TCP attempts at 250 ms and can explore additional address routes after TCP/ordinary TLS errors.
* `RetryAndFollowUpInterceptor.isRecoverable` excludes ProtocolException, post-send read timeouts, SSLPeerUnverifiedException and SSLHandshakeException caused by CertificateException.
* HTTP 403 and general 5xx do not cause route changes.
* HTTP 421 native handling is limited to a coalesced HTTP/2 connection; it does not generally mean try the DNS origin.
* HTTP 503 native retry only occurs with Retry-After: 0 and is not an origin-route switch.

Therefore DNS ordering alone is insufficient for requested fallback.

## Explicit fallback

On failed preferred TCP/TLS/header IO or HTTP 403/421/5xx, record a short host+preferred cooldown (e.g. 60 seconds), close the failed response body, then invoke the original chain once with original DNS and original timeouts. Return that result/error without another preferred retry. Do not retry cancellation (`call.isCanceled()`), thread interruption, or a generic InterruptedIOException; SocketTimeoutException may trigger fallback when the outer Call is not cancelled. Honor `Retry-After` if configured to retry statuses rather than indiscriminately duplicating load; 429 and ordinary 4xx should pass through unchanged.

Only errors observed before response delivery are eligible; do not replay a body already handed to playback/client. A mid-download/body error is left to existing player/application recovery. Response-body closure is essential before the second proceed.

Cap added connection latency by preferred-only connect timeout (e.g. min(original, 2000 ms)). Altering `readTimeout` affects returned response bodies as well; do not silently shorten playback read timeout merely to speed fallback. A bounded first-header watchdog would need extra complexity, so retaining current read timeout is acceptable if explicitly documented. Same Call retains overall timeout.

## Cache / concurrency

Use stable Dns equality and hashCode (delegate identity, original host, immutable preferred addresses and configuration identity) or a bounded wrapper cache, otherwise unique per-call Dns objects prevent OkHttp connection reuse. Preferred route Dns must differ from original Dns so fallback cannot reuse a pooled failed preferred connection. Config changes should invalidate routing caches and use existing asynchronous idle-pool eviction; never close TLS sockets on UI thread.

DNS lookup cancellation is not natively guaranteed by the Dns interface. Preferred DNS lookup should be bounded (e.g. max 2 seconds), cache positive answers briefly and negative answers briefly, deduplicate concurrent lookups, and use a bounded executor rather than unbounded threads. Return original resolution immediately on preferred lookup failure/saturation. Existing original DNS lookup behavior need not be broadened by this change.

## Meaningful tests

1. Disabled: no preferred DNS query, original Request unchanged.
2. CF original + CF preferred: preferred socket receives original URL/Host; original TLS peer name unchanged; ECH provider receives original host.
3. Mixed original addresses, non-CF, literal IP, HTTP, non-default port, hosts override: original route only.
4. Proxy and redirected inherited proxy, explicit client proxy and mixed proxy/DIRECT list: original proxy routing and auth retained; no preferred lookup.
5. Preferred DNS timeout/NXDOMAIN/non-CF/mixed: origin used immediately, negative cache prevents repeated delay.
6. Preferred refused TCP, TLS failure, read/header timeout: origin once, normal final response retained.
7. Preferred 403/421/500/502/503/525 and origin success: failed body closed, origin once; both fail returns second failure.
8. Preferred 404/429: no fallback; Retry-After honored where applicable.
9. Cancellation/deadline/interruption before or during preferred attempt: no origin replay.
10. POST/PUT/DELETE/one-shot/duplex: no new preferred routing/replay.
11. Concurrent callers, cooldown and setting changes: no cache corruption/deadlock/cross-host route leak; stable route DNS allows connection reuse.
12. Redirect from CF to non-CF/IP/new proxy: re-evaluate destination, preserve existing proxy credentials and stripping rules.
13. Body read failure after headers delivered: no automatic replay inside interceptor.

## Critical correction: same-Call connection reuse

Further inspection of actual `RealRoutePlanner.planReuseCallConnection` found that a connection already attached to a Call is checked only for health / noNewExchanges / host+port. It does NOT compare DNS or pool identity. Consequently merely changing Dns or ConnectionPool on the second proceed cannot guarantee a real edge switch after an HTTP status failure.

Recommended narrow adapter: tag each preferred attempt with per-Call state; a network interceptor captures its actual Connection. On fallback, mark that concrete `okhttp3.internal.connection.RealConnection.noNewExchanges()` before closing the failed response. This internal method marks no-new-streams and permits other in-flight HTTP/2 streams to finish. The second proceed releases the old allocation and acquires the proper original route. No socket.close or pool-wide active eviction is appropriate. This is one explicitly version-pinned internal API dependency and needs a real keepalive/TLS regression test; the public `Connection` interface in 5.5 has no equivalent method.

Capture actual route DNS identity, since the first preferred attempt might itself inherit a normal same-host connection from an earlier redirect; if it did not actually use the preferred route, do not retry a status failure unnecessarily. Network capture also distinguishes cached responses (no network attempt) from actual preferred failures.

Alternatives rejected: `Connection: close` on every preferred request disables healthy connection reuse (even though CallServerInterceptor uses this internally for HTTP/2 too); raw socket close breaks unrelated concurrent HTTP/2 streams; separate nested Calls require reconstructing the current chain policy and manually propagating cancellation/timeouts. DNS-only fallback cannot promise immediate recovery on HTTP failures.

## Final implementation decision: plan before altering chain timeouts

An intermediate lazy-DNS design was rejected in review because non-CF or failed preferred DNS would retain the original addresses but still inherit the preferred 2-second connect timeout and a different DNS pool identity. The implementation now resolves an ahead-of-time bounded cached plan, and only applies `withDns` / 2-second connect after obtaining usable Cloudflare addresses distinct from origin addresses. Every ineligible/failed-plan path calls the original chain unchanged. Original-host classification and preferred-host lookup each have a 1.5-second limit, share at most two workers, deduplicate in-flight lookups, cache conclusive answers for 60 seconds and resolver failures for 10 seconds. Once resolved, the constant RouteDns compares owner+generation+hostname+preferred hostname+address list for healthy pool reuse. Root requested and tests now cover actual socket connect timeout and warmed-pool identity for non-CF, mixed, NXDOMAIN and same-address cases.
