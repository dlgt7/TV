# wex JAR class compatibility audit

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../../../README.md)。

Read-only investigation, 2026-10-06. No user configuration, WebDAV content, device, or repository code changed.

## Confirmed failure

The user's 76-site wex configuration points at a 2026-09-24 JAR. Both remote and device-cached bytes have SHA256 `5284ad33611932ec2596075be4a158d7ccfedfcbb6f0c43cfeee112cfcd62982`.

Exactly three configured `csp_` entrypoints are absent from that JAR's class_defs, matching actual device ClassNotFoundException records:

- 师兄: `csp_WexAiV6DaShiXiongGuard`
- 太狗: `csp_WexAiV6TeGouGuard`
- 好盘: `csp_SoHaoSoGuard`

No configured ext is present for these three sources. This is a configuration/JAR version mismatch. Clearing the JAR cache or changing DoH cannot supply missing classes.

## Evidence-backed current-package migration candidate

Three independent public configurations now identify 师兄/大师兄 by the same current-package API and ext:

- https://github.com/DodgeZhang/tvbox/blob/main/wangerxiao/wex.json
- https://github.com/w1a2n3g4b5o/wex/blob/main/api.json
- https://github.com/ls125781003/tbapi1/blob/main/WEX/api.json

`api`: `csp_WexAppV7Guard`
`ext`: `{"config":"AppV7Dsx","proxyImg":false}`

The current user JAR defines WexAppV7Guard. This mapping is published, not inferred from class-name similarity. Still requires real loading/search validation before updating the user's entry. Existing public source row and private probe plan are saved as `w1a2n3g4b5o-latest-shixiong-site.json` and `current-appv7-shixiong-plan.json`.

No verified current-package replacement for 太狗 or 好盘 was found in those configurations.

## Legacy package candidates

### jieray

Source: https://github.com/jieray/tvapi/blob/main/WEX/spider.jar

Download location (public): `https://raw.githubusercontent.com/jieray/tvapi/main/WEX/spider.jar`

Local: `/tmp/tv-doh-jar-fix-20261006/missing-classes/jieray.jar`
SHA256: `21e332dcdb97a034e36ffaa71a1c3276cb3daba2d52271a546da0b61cc04b7c1`
MD5 for existing ;md5; loader support: `5089d5853a82e434dc5e7575c0e64214`

Missing from user configured APIs: ['csp_SoHaoSoGuard']

Present in this older package but absent from current package:

- `Lcom/github/catvod/spider/AppV6BaseGuard;`
- `Lcom/github/catvod/spider/WexAiV6DaShiXiongGuard;`
- `Lcom/github/catvod/spider/WexAiV6TeGouGuard;`

Present in current package but absent from this older package:

- `Lcom/github/catvod/spider/AiNewHuaJuanGuard;`
- `Lcom/github/catvod/spider/AnimeGgLoveGuard;`
- `Lcom/github/catvod/spider/AnimeJiongCiYuanGuard;`
- `Lcom/github/catvod/spider/BookHeMaGuard;`
- `Lcom/github/catvod/spider/BookHongGuoGuard;`
- `Lcom/github/catvod/spider/NativeFile;`
- `Lcom/github/catvod/spider/SoKaKaGuard;`
- `Lcom/github/catvod/spider/WexAppV7Guard;`
- `Lcom/github/catvod/spider/WexFengYe4KGuard;`

### rose

Source: https://github.com/zhaoyunling/rose4KTv/blob/main/newRoseTv.json

Download location (public): `http://oss4liview.moji.com/thd_file/2026/08/08/b8d31fe80fe7a6115344ae8344894278.jpg`

Local: `/tmp/tv-doh-jar-fix-20261006/missing-classes/rose.jar`
SHA256: `9c1d1bbe2de8fc98786b267fed7ba646975aa5518cf21882a189f2965bfd48f3`
MD5 for existing ;md5; loader support: `2a6a34bbdb7a9e19a5caeae64af0320e`

Missing from user configured APIs: []

Present in this older package but absent from current package:

- `Lcom/github/catvod/spider/AppV6BaseGuard;`
- `Lcom/github/catvod/spider/SoHaoSoGuard;`
- `Lcom/github/catvod/spider/WexAiV6DaShiXiongGuard;`
- `Lcom/github/catvod/spider/WexAiV6TeGouGuard;`

Present in current package but absent from this older package:

- `Lcom/github/catvod/spider/AiNewHuaJuanGuard;`
- `Lcom/github/catvod/spider/AnimeGgLoveGuard;`
- `Lcom/github/catvod/spider/AnimeJiongCiYuanGuard;`
- `Lcom/github/catvod/spider/BookHeMaGuard;`
- `Lcom/github/catvod/spider/BookHongGuoGuard;`
- `Lcom/github/catvod/spider/NativeFile;`
- `Lcom/github/catvod/spider/SoKaKaGuard;`
- `Lcom/github/catvod/spider/WexAppV7Guard;`
- `Lcom/github/catvod/spider/WexFengYe4KGuard;`

The rose package URL is dated 2026-08-08 and defines all 76 of the user's configured csp APIs, including the three missing original entrypoints. This does not establish current endpoint functionality. The private `legacy-three-plan.json` retains the original site entries and user's same search input for isolated testing; it contains private data and must not be published.

## Per-site JAR mechanism verified

In `/home/ubuntu/TV-ui-redesign`:

- `app/src/main/java/com/fongmi/android/tv/bean/Site.java:130`: `Site.objectFrom()` accepts serialized `jar`; only an empty site JAR inherits the global spider.
- `app/src/main/java/com/fongmi/android/tv/bean/Site.java:330`: site passes its jar through BaseLoader.
- `app/src/main/java/com/fongmi/android/tv/api/loader/BaseLoader.java:62`: csp API delegates to JarLoader with the site JAR.
- `app/src/main/java/com/fongmi/android/tv/api/loader/JarLoader.java:153`: separate JARs use distinct md5(jar) loader keys and md5(jar)+siteKey Spider cache keys.
- `app/src/main/java/com/fongmi/android/tv/api/loader/JarLoader.java:121`: supports http/file JAR and optional `;md5;` pin.

Thus verified old entrypoints could use a site-level JAR override without downgrading the other 73 entries. The two native-packed versions should also be tested together in one process before deployment: each uses its own DexClassLoader and extracts a random `.wexfnw` native file, but both have native initialization/cleanup behavior outside host control.

## Recommended next decision

1. Test the published AppV7Dsx migration against current JAR first.
2. Test only unresolved original sources against the legacy rose JAR, isolated from the user's normal app. Successful construction alone is insufficient; require real search data, then playback if selecting it for production.
3. If legacy endpoints remain live, use only affected per-site jar fields, with hash pin; preserve user's remaining config and source settings.
4. If not live, retain them until a verified alternative exists and report the upstream condition precisely. Do not invent aliases or globally replace/downgrade the main JAR.
