# TVBoxOSC 系分支功能迁移评估（2026-09-21）

评估对象：

| 仓库 | 星标 | 体积 | 最后推送 | 许可 |
|---|---|---|---|---|
| [takagen99/Box](https://github.com/takagen99/Box) | 3044 | 194.7 MB | 2026-02-27（已停滞 ~7 个月） | AGPL-3.0 |
| [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) | 2892 | 45.8 MB | 2026-09-17（4 天前，活跃） | AGPL-3.0 |
| 本 fork（FongMi/TV 系） | — | — | — | **GPL-3.0** |

## 一、结论

**整体迁移不可行，只能做点状迁移。** 这两个项目和本 fork 属于两个技术世代，除少数自包含工具类外，没有可直接搬的东西。真正值得动手的只有 4 项，其中 **Brotli 解压**（修一个现存缺陷）和 **M3u8 去广告**（我们完全没有的能力）价值最高。

## 二、为什么不能整体迁移：技术栈已经分岔

两个源仓库都是 **TVBoxOSC 血统**，本 fork 是 **FongMi/TV 血统**：

| 层 | takagen99 / q215613905 | 本 fork |
|---|---|---|
| 播放内核 | ExoPlayer **2.19.1** + IJK（`tv/danmaku/ijk/media`）+ DKVideoPlayer（`xyz/doikki/videoplayer`，含 aliplayer 后端） | Media3 **1.10.1** + `media3compat` 源码编译 libmpv / FFmpeg / dav1d |
| 网络 | OkGo（`com.lzy.okgo`） | OkHttp 5.4.0 |
| 存储 | Room + Hawk | Gson + Prefers |
| 包名 | `com.github.tvbox.osc` | `com.fongmi.android.tv` |
| 构建 | minSdk 19 / compileSdk 33 / Java 8 | minSdk 24 / targetSdk 37 / Java 21 / AGP 9.2.1 |

播放层、网络层、存储层、UI 层全不兼容。任何"搬一个模块过来"的尝试都会变成重写。

## 三、已核实：这些看着像新功能，我们其实已经有

分析时先排除了三个假阳性，避免重复造轮子：

| 他们的实现 | 我们的等价物 |
|---|---|
| `util/net/OkProxySelector.java`、`ProxyAuthenticator.java` | **同名文件已在** `catvod/src/main/java/com/github/catvod/net/` |
| vendored `okhttp3/dnsoverhttps/*`（3 个文件） | 用库 `okhttp-dnsoverhttps`（okhttp 5.4.0），逻辑在 `catvod/net/OkDns.java` |
| `server/CacheRequestProcess.java`（`/cache?do=get\|set\|del`） | `app/.../server/process/Cache.java` 是**同一套 HTTP 接口**，仅后端由 Hawk 换成 Prefers |

## 四、值得迁移的（按性价比排序）

### 1. Brotli 响应解压 —— 最强候选，本质是修一个现存缺陷

**现状（我们的问题）**：`catvod/build.gradle:24` 声明了 `api libs.brotli`（`org.brotli:dec:0.1.2`），但全仓库 `org.brotli` / `BrotliInputStream` 的 Java 引用数为 **0** —— 这个依赖白声明了，从未生效。部分爬虫源与 CDN 只回 `Content-Encoding: br`，现在会拿到二进制乱码。

**他们的实现**（takagen99/Box）：
- `util/urlhttp/BrotliInterceptor.java`（72 行）：请求无 `Accept-Encoding` 时补上 `br,gzip`，响应头为 `br` 时解压
- `util/urlhttp/internal/BrotliSource.java`（17 行）：`new BrotliInputStream(source.inputStream())` 包成 okio `Source`

**价值**：等于把一个已付出依赖成本的库激活，风险极低。

**风险**：它引用了 `okhttp3.internal.http.RealResponseBody`（okhttp 内部 API）。okhttp 5.4.0 下该内部类已变动，需改为 `Response.Builder().body(...)` 构造。**必须适配，不能直接复制。**

### 2. M3u8 去广告 —— 我们完全没有的能力

**现状**：本 fork **没有 M3u8 类**（`find -name "M3u8*.java"` 为空，`upstream/fongmi` 也没有），也没有任何广告片段识别（`grep -i "advert|preroll|midroll|vast|vmap|EXT-X-CUE"` 零命中）。

**他们的实现**（q215613905/TVBoxOS，`util/M3u8.java`，**959 行**）：

类注释明确写着 `@author asdfgh, FongMi` + `Based on FongMi/TV` —— **这是从我们上游拿过去再增强的**，所以思路和我们的代码风格天然接近。核心增量是广告分片识别：

```java
// 增强：广告片段 URL 特征识别（去广告接口常用规则）
private static final Pattern REGEX_AD_SEGMENT_URI = Pattern.compile(
    "(?i)(^|[/?&=_.-])(ads?|adv|advert(ise(ment)?)?|commercial|preroll|pre-roll|midroll|mid-roll|postroll|post-roll|sponsor|scte|vast|vmap|interstitial|bump...
```

配套还处理 `#EXT-X-DISCONTINUITY`、`#EXT-X-CUE-OUT/IN`、`#EXT-X-DATERANGE`、`#EXT-X-KEY`、`#EXT-X-MAP`。

**价值**：用户可感知的功能（去广告），且是我们唯一"零基础"的候选。

**风险**：
- import 了 `com.google.android.exoplayer2.util.UriUtil`（ExoPlayer2），需换成 `androidx.media3.common.util.UriUtil`
- 它不是独立可用的工具类，要接进我们的代理链路（`app/.../server/process/Media.java` 或 Proxy），涉及请求重写与分片过滤，**这是 4 项里工作量最大的**

### 3. EPG 频道名模糊匹配 —— 高价值，但建议只取数据、重写代码

**现状（我们的问题）**：EPG 匹配是**精确匹配**。`app/.../api/parser/EpgParser.java:190`：

```java
return channels.stream().flatMap(x -> x.getDisplayName().stream())
        .map(Tv.DisplayName::getText).filter(name -> !name.isEmpty())
        .filter(liveChannelMap::containsKey)   // ← 必须完全相等
        .findFirst().map(liveChannelMap::get).orElse(null);
```

EPG 源常写「CCTV-1 综合」而直播源写「CCTV1」，这种写法直接漏配，EPG 大面积不显示。

**他们的实现**（`util/EpgNameFuzzyMatch.java`，113 行）：从 assets 读 `Roinlong_Epg.json`（一份**人工整理的频道名别名对照表**），建 Hashtable 做归一化后匹配。

**价值**：EPG 命中率是直播体验的刚需，改动面小。

**风险（它的实现有明显缺陷，不要照搬）**：
- 依赖 `OkGo` 和 `App.getInstance()`，与我们的栈不符
- 有硬编码兜底地址 `http://www.baidu.com/maotv/epg.json` —— 第三方地址、随时失效、且是明文 http
- **建议只取「别名表 + 归一化匹配」的思路，代码用我们的 `EpgParser` 风格重写**；那份 JSON 的数据来源与许可需要单独确认

### 4. 音轨记忆 —— 小而实用

**现状**：`PlayerSetting.java` 里没有任何音轨持久化（`grep audioTrack` 零命中），每次播放都要重新选音轨。

**他们的实现**（`util/AudioTrackMemory.java`，50 行）：SharedPreferences 按 `playKey` 存 `(groupIndex, trackIndex)`，exo / ijk 分开命名空间。

**价值**：小改动，体验提升明确。

**风险**：低。接入时**只需一份实现**（我们是 Media3，不必保留 ijk 那套），且 key 要换成我们的播放 key 体系（`PlayerSetting` 的 engine / vod key）。

### 5. 条件候选（先比对再决定）

- `pyramid/src/python/crypto_protocol_dh.py`（q215613905 独有）—— DH 密钥交换协议的 Python 实现。若将来遇到需要 DH 握手的站点可参考。
- `util/CharsetUtils.java` —— 字符集嗅探，可能与我们的 `juniversalchardet` 重叠，需先比对。
- `util/UTF8BOMFighter.java` —— 处理响应体 BOM；与我们踩过的"Java 源码 BOM"是不同场景，价值有限。

## 五、明确不建议迁移

| 目标 | 理由 |
|---|---|
| IJK 播放器（`tv/danmaku/ijk/media`，27 文件 + 一堆 .so） | 我们已是 Media3 1.10.1 + 源码编译 libmpv；迁 IJK 是技术倒退 |
| DKVideoPlayer（`xyz/doikki/videoplayer`，含 aliplayer 后端） | 第三方播放器框架，与 `media3compat` 架构直接冲突 |
| 独立 `player/` 模块（q215613905，76 文件） | minSdk 19 / targetSdk 28 / compileSdk 33 / Java 8 / ExoPlayer2 —— **整套遗留栈**，与 minSdk 24 / targetSdk 37 / Java 21 / Media3 不兼容 |
| `pyramid/` Python 爬虫引擎 | 与我们的 `chaquo`（Chaquopy）是同一件事的两种实现；我们已有 lxml / ujson / pyquery / requests / cachetools / pycryptodome / beautifulsoup4，功能更全 |
| `xwalk/`（takagen99，4 个 Crosswalk zip，也是它仓库 194 MB 的主要来源） | Crosswalk 是给 Android 4.4–6 老设备用的（当时 WebView 无法独立升级）。我们 **minSdk 24**，WebView 可独立升级，毫无必要 |
| `util/IpScanning.java` | 实现有缺陷：`while (!threadPool.isTerminated()) {}` 忙等自旋烧 CPU，且用途不明 |
| `StorageDrive` / `SearchHistory` / `VodRecord` 等 Room 表（takagen99） | Room schema 与 FongMi 的存储体系不兼容 |

## 六、许可证约束（动手前必须处理）

- 本 fork 与上游 FongMi/TV 均为 **GPL-3.0**
- takagen99/Box 与 q215613905/TVBoxOS 均为 **AGPL-3.0**

AGPLv3 第 13 条允许与 GPLv3 代码组合，但**组合后的整体必须按 AGPL-3.0 发布**，即会引入"网络服务使用也需提供源码"的额外义务。因此直接复制这两个仓库的代码，等于要求本项目整体转 AGPL-3.0。

三条可选路径：
1. **接受 AGPL-3.0** —— 需评估这是否符合你的分发意图
2. **按思路自行实现**（推荐用于第 1、3、4 项这类小工具）—— 思路与算法不受版权保护，重写不构成衍生
3. 联系原作者取得授权

第 2 项的 M3u8 去广告涉及较多原创代码结构，若采用建议同样走"理解思路后重写"，顺带解决 ExoPlayer2 → Media3 的适配。

## 七、建议执行顺序

1. **Brotli 解压**（半天）—— 激活死依赖，风险最低，先拿它验证迁移流程
2. **音轨记忆**（半天）—— 独立、无耦合
3. **EPG 模糊匹配**（1 天）—— 需先确认别名表数据来源
4. **M3u8 去广告**（2–3 天）—— 工作量最大，需接入代理链路，建议单独排期

四项全部需要按 GPL/AGPL 约束决定实现方式，**不要直接复制文件**。
