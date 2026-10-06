# TV

<p align="center">
  <img src="other/image/logo.svg" width="128" alt="TV Logo">
</p>

简体中文 | [English](README.en.md)

基于 [FongMi/TV](https://github.com/FongMi/TV) 和 [CatVod](https://github.com/CatVodTVOfficial/CatVodTVJarLoader) 的 Android 影音应用，支持 **Android TV 大屏** 和 **Android 手机** 两种使用场景，并通过外部配置扩展点播、直播、解析、弹幕、字幕和爬虫能力。

Bug请提交Issue

[![Star History Chart](https://api.star-history.com/svg?repos=wobuhui666/TV&type=Date)](https://www.star-history.com/#wobuhui666/TV&Date)

---

## 目录

- [项目架构](#项目架构)
- [免责声明](#免责声明)
- [本 Fork 改了什么](#本-fork-改了什么)
- [播放器](#播放器)
- [点播功能](#点播功能)
- [直播功能](#直播功能)
- [爬虫引擎](#爬虫引擎)
- [网络功能](#网络功能)
- [DLNA 投放](#dlna-投放)
- [Android Auto](#android-auto)
- [远程控制](#远程控制)
- [配置说明](#配置说明)
- [构建命令](#构建命令)
- [延伸阅读](#延伸阅读)

---

## 免责声明

本项目仅供学习、研究和个人合法使用，不内置、不存储、不分发任何影视资源、直播源、解析接口或爬虫规则。外部配置、第三方接口及其返回内容均由用户自行添加和管理，相关版权、合规和使用风险由使用者自行承担。

请在遵守所在地法律法规和版权要求的前提下使用本项目，支持正版内容。

---

## 项目架构

| 项目 | 值 |
| --- | --- |
| package | `com.fongmi.android.tv` |
| minSdk | 24（Android 7.0 Nougat） |
| abi | `arm64-v8a`、`armeabi-v7a` |
| flavor | `leanback`（电视版）、`mobile`（手机版） |

```text
TV/
├── app/            主应用（包含 leanback 与 mobile 两套 UI）
├── catvod/         爬虫抽象层（Spider 接口、OkHttp 网络栈）
├── quickjs/        QuickJS JavaScript 引擎
├── chaquo/         Chaquopy Python 引擎
├── docs/           配置、Spider、本地 API、直播源文档
└── Release/apk/    CI 或本地 release 产物输出目录
```

`app/src/main/` 存放两端共用业务逻辑，`app/src/leanback/` 存放电视端 UI，`app/src/mobile/` 存放手机端 UI。

---

## 本 Fork 改了什么

这个仓库是 `wobuhui666/TV` 维护的个人增强版，重点改动集中在 TV 大屏交互、mpv 播放体验、弹幕字幕、爬虫兼容和构建流程。

### 1. JetStream 风格 TV UI

- 新增并持续完善 JetStream 风格电视端界面，包括首页、详情页、播放控制层、推送页、站点选择、线路选择、选集、速度、UA、字幕等页面和弹窗。
- 大量重做 leanback 布局组件，增加 `JetStream*` 自定义 View、焦点态、动效、圆角、阴影、卡片比例、海报信息标签和播放控件样式。
- 使用 Material Symbols Rounded 图标资源，统一 TV 端按钮、控制栏、设置项和弹窗图标风格。
- 优化大屏遥控器操作体验，补充长按命令处理、焦点移动、弹窗尺寸、framed 属性和不同弹窗的安全边距。
- 优化首页推荐海报加载，新增 `FeaturedPosterCache`，减少重复请求并提升封面加载稳定性。

### 2. 播放器与 mpv 增强

- 增强 mpv 播放链路，补充文件加载状态、首帧渲染标记、seek 逻辑、日志输出和 HTTP header 传递。
- 增加 mpv 相关设置入口，包括 `mpv.conf` 查看/编辑/导入、Vulkan、`gpu-next`、Anime4K 着色器档位等选项。
- 新增 `MpvAnime4K` 支持，用于动画内容的上采样与锐化场景。
- 修复和优化播放边界问题，包括外部播放器长进度、重复控制栈溢出、横竖屏处理、选集位置归一化、倒序选集时保持当前集位置等。
- 增加顶部字幕信息显示控制，让播放页上方信息与字幕显示可独立控制。

### 3. 字幕与弹幕能力

- 重做字幕弹窗布局和控制按钮，补充字幕放大、缩小、上移、下移、重置等入口，提升 TV 与移动端的字幕调节体验。
- 增加 Logvar 弹幕 API 支持，可配置 Logvar 根地址，自动通过播放 URL 或片名集数匹配弹幕，失败后再回退到旧弹幕接口。
- 为 Logvar 地址归一化、URL 构造、剧集匹配、返回解析等逻辑增加单元测试。
- 保留远程字幕/弹幕注入能力，可通过本地 HTTP API 推送字幕或弹幕到当前播放器。

### 4. 爬虫、解析与脚本兼容

- 为 QuickJS 增加 drpy 兼容层，补充 `pdfh`、`pdfa`、`pd`、`pdfl` 等常用解析方法适配，降低迁移旧脚本的成本。
- 增加 `jsoup` 依赖和 drpy 相关解析实现，提高 HTML 解析稳定性。
- 增强 `Connect` 内容处理和脚本 patch 能力，便于对外部脚本做运行时兼容修正。
- 集成 QuickLog，便于 JavaScript 爬虫、解析过程和异常场景的日志排查。

### 5. 网络、数据与体验修复

- 优化 HTTP 响应处理和播放失败后的 fallback 流程，提高换源、线路切换和解析失败时的容错性。
- 调整热门搜索接口和 TMDB 代理接口，减少接口变动对页面展示的影响。
- 修复多处 TV 大屏尺寸定义、进度条、卡片形状、弹窗关闭标签、UI 间距和文字样式问题。
- 补充崩溃日志复制入口，方便在设备上直接提取错误信息。

### 6. 构建与测试

- 增加 release APK 构建和签名相关工作流，覆盖 `arm64-v8a` 与 `armeabi-v7a`。
- 对齐 Gradle、AGP、AndroidX 和本地 Media3 composite build 相关配置，降低本地构建差异。
- 增加 `testInstrumentationRunner`、JUnit 依赖和若干播放器/状态/弹幕相关测试。

---

## 播放器

- **核心**：ExoPlayer（Media3）+ FFmpeg 软解，支持硬解 / 软解自动降级切换。
- **mpv**：支持 mpv 播放、配置文件编辑、Vulkan、`gpu-next`、Anime4K、播放日志和 HTTP header。
- **渲染**：SurfaceView / TextureView。
- **DRM**：Widevine、PlayReady、ClearKey，支持 `#KODIPROP` 声明。
- **弹幕**：DanmakuFlameMaster，和播放时间轴同步，支持远程推送与 Logvar 匹配。
- **字幕**：SRT / SSA / ASS 外挂字幕、系统 CaptioningManager、远程实时注入。
- **其他**：倍速、多缩放比例、画中画（PiP）、背景音频、片头 / 片尾自动跳过。

---

## 点播功能

- 多站点分类浏览，支持 Filter 筛选（年份 / 地区 / 类型等）。
- 多站点并行搜索，关键字自动繁转简提升兼容性。
- 播放失败自动换源：解析器 -> 线路 -> 搜索其他站 -> 下一站点。
- 观看记录（保留 60 天）、收藏、无痕模式。
- 电视版支持遥控器操作；手机版支持手势调节亮度、音量、进度、上下滑切集、屏幕旋转与锁定。

---

## 直播功能

- 支持 M3U、TXT（`#genre#` 分组）、JSON 三种直播源格式。
- **EPG**：XMLTV 格式，支持 `.gz`，每 6 小时自动刷新。
- **追看 / 时移**：支持 `append`、`pltv` 等多种类型。
- 支持频道收藏、隐藏分组密码保护。
- 特殊引擎：TVBus、ForceTech。

---

## 爬虫引擎

支持三种语言编写爬虫：

- Java JAR（DexClassLoader）
- JavaScript（QuickJS）
- Python（Chaquopy）

通过 `api` 字段指定爬虫，通过 `ext` 字段传入初始化参数。完整 API 规格见 [SPIDER.md](docs/SPIDER.md)。

同时支持部分 [Forward Widget](https://github.com/InchStudio/ForwardWidgets) 模块：`api` 可直接填写 Forward 单文件 `.js` 或 `.fwd` 合集地址。当前支持 `video` / `list` 视频模块的列表、筛选、详情、搜索、播放地址和自定义播放 header；`stream` 模块可按片名搜索并作为直接播放来源。兼容同步 `Widget.html.load`、同步或 `await` 形式的 `Widget.storage`，并支持缓存 TTL。TMDB 请求会优先使用 `ext` 中的 token/key，未配置时回退到应用构建的 `TMDB_API_KEY`；相对海报路径会自动补全为 `https://image.tmdb.org/t/p/...`。Bangumi/TMDB 这类资料源本身不含视频，开启 `changeable` 后可沿用应用现有换源逻辑：无播放线路时按片名搜索其他可换源站点并播放。暂不支持 `danmu`、`subtitle`、`requiresWebView: true` 和 `FWENC1` 加密 Widget。配置示例见 [CONFIG.md](docs/CONFIG.md#forward-widget-配置)。

---

## 网络功能

- **DoH**：DNS over HTTPS，支持 Bootstrap IP。
- **代理**：HTTP / HTTPS / SOCKS4 / SOCKS5，可按 host 正则规则动态选择。
- **Hosts**：DNS 解析覆盖，支持通配符 `*`。
- **CORS 注入**：按 host 规则在响应中注入自定义 header。
- **广告拦截**：通过 `ads` 黑名单拦截匹配域名。
- **WebView 嗅探**：Sniffer 通过 regex 拦截媒体 URL，支持 UA 伪装。

---

## DLNA 投放

- **DMC（投放端）**：手机版扫描局域网 DLNA 设备并投放媒体。
- **DMR（被投放端）**：电视版作为 DLNA Renderer 接收其他设备投放。

使用 JUPnP 3.0.4（UPnP），支持 play / pause / stop / seek / next / repeat 控制，可传递自定义 HTTP header（User-Agent、Referer 等）到目标串流。

---

## Android Auto

电视版支持 Android Auto。`PlaybackService` 实现 `MediaLibraryService`，可在车机上浏览播放记录与直播频道：

- **点播**：历史记录条目可直接续播，恢复上次进度。
- **直播**：按分组浏览频道，可直接选台。
- **播放控制**：支持车机端 play / pause / prev / next / stop。
- **懒加载**：App 退出后 Auto 仍保持连接，配置会自动重新载入。

---

## 远程控制

应用启动后会绑定本地 HTTP 服务器（NanoHTTPD），端口号从 **9978** 起自动探测到 **9998**，可用于播放控制、推送字幕 / 弹幕、多设备同步等。完整端点见 [LOCAL.md](docs/LOCAL.md)。

---

## 配置说明

Vod 配置是应用主要入口，可通过 URL 或本地路径载入，顶层字段包括：

- 点播站点（`sites`）、解析规则（`parses`）
- 直播来源（`lives`）
- 网络设置（`doh`、`proxy`、`hosts`、`ads`）
- 弹幕设置（`danmaku`、`logvar`）

Live 配置可内嵌或独立存放。完整字段说明见 [CONFIG.md](docs/CONFIG.md)。

---

## 构建命令

```bash
chmod +x ./gradlew
./gradlew :app:assembleLeanbackArm64_v8aDebug
./gradlew :app:assembleMobileArm64_v8aDebug
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest
```

复现 CI release 构建：

```bash
./gradlew assembleLeanbackArm64_v8aRelease assembleLeanbackArmeabi_v7aRelease --no-daemon --build-cache --max-workers=2
```

release 构建需要 JDK 21、Python 3.10、Android SDK 37、`local.properties` 中的签名配置，以及使用本地 Media3 composite build 时的 `MEDIA3_SOURCE_DIR`。更多本地环境说明见 [LOCAL_BUILD_ENV.md](LOCAL_BUILD_ENV.md)。

---

## 延伸阅读

| 文档 | 说明 |
| --- | --- |
| [CONFIG.md](docs/CONFIG.md) | Vod / Live 完整配置字段说明 |
| [SPIDER.md](docs/SPIDER.md) | Spider 所有方法规格与返回格式 |
| [LOCAL.md](docs/LOCAL.md) | 本地 HTTP API 所有端点说明 |
| [LIVE.md](docs/LIVE.md) | 直播来源格式说明 |
