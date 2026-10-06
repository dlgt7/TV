# Sorani legacy Spider source — 2026-10-06

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../../../README.md)。

Only app/src/main/java/com/github/catvod/spider/Sorani.java was added in CatVodSpider. UTF-8/CRLF verified (363 lines). Uses inherited Spider.client(), okhttp3 and org.json; no Net, Local, Result/Vod beans, newly hosted helper classes, or independent client. Existing dirty Girigiri.java was left untouched.

Supported: dynamic video categories and category-specific official tags/status filters, sorting/year/initial filters, home/latest, category and search pagination, details/actors, per-line episode orders, per-play permission check and fresh ticket URL, fallback resolution of episode ID from official order endpoint, mirror media mapping preserving encoded query, own-call cancellation.

Configuration ext can be a host string or JSON {"host":"https://mirror.example","apiBase":"https://mirror.example/__upstream__/api.sorani.cc/sorani-cms"}. apiBase may optionally end in /api. Default host remains the user's mirror. There is no automatic direct-origin failover. Known API/image/video upstream addresses are mapped through /__upstream__/; unknown playback origins fail explicitly. The deployed reverse proxy handles HLS playlist/key/segment rewriting.

Authoritative frontend evidence:
- chunks_wRMOQlSa.js: API functions b/list, _/detail, D/lines, M/line orders, C/order resolver, H/play; GET /api/video, /api/video/{id}, /api/video/{id}/play-lines, /api/video/{id}/play-lines/{code}/episode-orders, /api/video/episode/video/{id}/episode/{order}, /api/video/episode/{episodeId}/play?lineCode=...
- nodes_10.BkNxSwPb.js: category tree, current/size/total/pages, allowed sort/status/year/initial filters and search relevance_popular mode.
- nodes_15.CzaQoT4q.js: episode and actor structures, line-specific order mapping.
- chunks_BVPakrd2.js: canPlay and playUrl gate, fresh permission request, hls flag.
- Existing D26fZ3l1.js and /home/ubuntu/sorani-proxy/snippet.mjs: API prefix, image normalization and mirror host mapping.

Current live status differs from 2026-10-05 outage: normal browser UA and mirror Referer/Origin received HTTP 200/code 200 for all listed APIs in this run. See api-probe.json, pagination-probe.json, and hostcheck-results.json. Java host check ran the actual Sorani.java against real mirror responses: 5 categories, 24 home videos; page 1 first ID 4757 and page 2 first ID 4536; total 4714/pages 197; search 无职 yielded 8 and a unique nonexistent title yielded 0; detail 4634 has 14 episodes and resolves a mirror playback URL.

media-probe.json confirms play API, HLS playlist, 16-byte AES key and 878160-byte MPEG-TS first segment all HTTP 200 through the mirror. This is transport validation, not Android decoding/playback validation.

fixture-results.txt uses actual saved API bodies behind a temporary local HTTP server to verify params, paging, episode resolver, media mapping/ticket preservation, and explicit HTTP/API/malformed JSON/permission/cancel errors. One initial live harness requested the same episode's play ticket twice immediately and got the site's '播放请求过于频繁，请稍后再试'; the source propagated it correctly. No retry/bypass was added; the final host run requests only one ticket and passes.

Java --release 17 compilation passes against the current TV legacy Spider source; current CatVodSpider compile-only SDK lacks static client(), which root is restoring before the final spiderJar build. Host check uses a temporary shared JVM OkHttp provider strictly outside the repo. Full JAR/R8/app loading and actual decoder validation are for the root task; no device changed here.
