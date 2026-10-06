# girigiri 愛動漫 source investigation (2026-10-06 UTC)

Base: https://gl.ciallo0d000721.cc.cd
Classification: ordinary anime streaming catalog; home navigation exposes 日番 and 劇場版, predominantly mainstream anime. Do not include third-party ad/navigation links as categories.

## Routes and parsing

Home GET `/` (200, no cookies). Lists HTML cards `.public-list-box`; each card has `a.public-list-exp[href][title]`, image `img[data-src]`, remark `.public-list-prb`. Cards may repeat across sections; deduplicate by href. Resolve relative cover URL against base.

Categories: 2 日番, 3 美番, 21 劇場版, 20 真人番劇, 24 BD副音軌, 26 演唱會&周邊活動&其他. Extract from category-page channel filter instead of external header links.

Category GET `/show/2-----------/`, pagination `/show/2--------2---/` (48 cards on each, distinct first ids; first-page last-page link=66).

Path fields, split on hyphen: `[id, area, by, class, lang, letter, level, limit, page, state, tag, year]`, append `/version/{version}/` for version. URL-encode each value. Relevant filters found from `<li data-type=... data-val=...>` are in filters.json. `area` is season (一月/四月/七月/十月), `class` genre, `year` year, `lang` 日语/国语, `version` TV番/泡面番/OVA/轻动画, `state` 小说改/漫画改/游戏改/原创. Sort `by` = time (最新), hits (最热), score (评分).

Successful compound filter `/show/2--hits-%E7%A7%91%E5%B9%BB-----1---2025/` returns 11 items and first `/GV26649/` 胆大党 第二季. Pagination selectors may use link text 下一页/尾页; inspect href.

Detail GET `/GV26868/`: title `.slide-info-title`; cover `.detail-pic img[data-src]`; remark `.slide-info-remarks.cor5`; description `#height_limit`; year first `.slide-info` links; director/actor/genre via `.slide-info.partition` rows and `strong` label. `.anthology-tab a` source labels have badge episode counts (remove `.badge` before text). Each corresponding `.anthology-list-box` contains `.anthology-list-play li a[href]`.

Example source labels 繁中/简中 with 3 episodes each. Episode URLs `/playGV26868-1-1/` and `/playGV26868-2-1/`, first episode text 先行01.

## Search

The site's standard GET form `/search/-------------/?wd=JOJO` returns a verification form, not search results. Public suggestion endpoint documented by site's script.js succeeds without cookies:
`GET /index.php/ajax/suggest?mid=1&wd=JOJO&limit=20`
Schema `{code:1,msg,page:1,pagecount:1,limit:20,total:7,list:[{id,name,en,pic}],url}`; map id to `/GV{id}/`, resolve pic relative. Saved response suggest.json has 7 results.
Warning: endpoint ignores both page=2 and pg=2; verified using limit=2, both return same first two ids and page=1. Treat as a bounded first-page suggestion search, or fetch a chosen limit and paginate locally; do not claim server-side pagination.

## Playback and request headers

Play page includes `var player_aaaa={...}`. Fields include encrypt, url, url_next, from, id, sid, nid, vod_data. Use a balanced JSON parser or the script body extraction, not a regexp that stops at the nested vod_data object's first closing brace. encrypt=2 => Base64 decode then URL decode (MacCMS format); result here is same-origin https playlist.m3u8 without query parameters. Source from=cht/chs (繁中/简中). player-schema.json and play.txt retain schema with only redacted media URLs.

The normal playerconfig.js parse URL is `https://pl.girigirilove.com/addons/aplyer/atom.php?key=[REDACTED_QUERY]&url=` but direct decoded HLS works; no parser request needed.

Media probes: Python default UA fails 403. `User-Agent: Mozilla/5.0` alone gives HTTP200, `application/vnd.apple.mpegurl`, #EXTM3U, 120586 bytes. Referer alone with Python default UA still403. Browser UA + site-page Referer succeeds; browser UA + original girigirilove.com Referer also succeeds. Recommend normal browser UA and base Referer.

First media segment resolved from HLS is same host, .ts, no query. GET with browser UA, base Referer, Range bytes=0-1023 gives HTTP206 video/mp2t,1024 bytes, TS sync first byte0x47. No cookies, authentication, user tokens, or JS runtime needed for tested playback.

## Limits

Home/latest includes announced future episodes, so choose known older playable title for device test. Search standard form remains captcha gated; suggestion search works but lacks true paging. Full media URLs / response cookies were redacted from retained evidence; only metadata and short structural probes retained.
