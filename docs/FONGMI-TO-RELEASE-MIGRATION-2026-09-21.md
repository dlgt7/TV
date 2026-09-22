# fongmi → release 功能与播放器优化迁移方案

日期：2026-09-21  
目标分支：`release`  
供体分支：`origin/fongmi`（`bdecc5c223062f436df692b827de451ece37167a`）
状态：**36 项提交已逐项处置；最新优化批次正在重新构建和设备回归，网络协议与投屏端到端回归待完成**

## 1. 结论

以 `release` 为唯一基线，将 `fongmi` 作为功能供体进行语义迁移；不执行整分支 merge，也不以 `fongmi` 为基线反向移植 `release`。

原因：

- `release` 已包含更新的上游、Room schema 35、Media3 1.10.1 公共 API 播放栈，以及 preload、播放恢复、双字幕、MediaSession、解码迁移和 P3 音视频效果等改造。
- `fongmi` 停留在较旧的数据模型和播放器生命周期，Room schema 为 27。
- 两分支从 `96c33f3a7` 后分叉：`origin/fongmi` 独有 36 个提交；本次审计严格以该远端引用为供体，不使用历史不同的 `upstream/fongmi`。
- `origin/fongmi` 的价值除 AirPlay、SMB/WebDAV、DLNA 和投屏管理外，还包括 action 刷新、首页焦点、直播选线、PNG-TS、MPV 恢复等小型优化，均按当前架构语义迁移。

## 2. 分支基线

| 项目 | 值 |
|---|---|
| 共同祖先 | `96c33f3a7bc62b4a34fc64854fab50c5a5ae3153` |
| `release` 已推送基线 | `7433602ca68e51da271910f1058ba4bc1b63f65d` |
| `origin/fongmi` 头 | `bdecc5c223062f436df692b827de451ece37167a` |
| 非供体分支 | `upstream/fongmi`（另一条 179 提交历史，不纳入本次处置） |
| GitHub 比较状态 | `diverged` |

## 3. 必须迁移的功能

### 3.1 AirPlay / Apple 屏幕镜像

供体提交：

- `65c2cad1` `feat(airplay): 集成AirPlay服务及增强视频播放管理`
- `32605bdf` `feat(cast): TV 投屏设置与 AirPlay 播放页，并处理互斥与资源释放`
- `b30d3a92` `feat(network): 实现网络切换后Cast服务自动重绑定功能`

主要供体代码：

- `airplay/`
- `AirPlayService.kt`
- `AirPlayCastActivity.java`
- `AirPlaySetting.java`
- `SettingAirPlayActivity.java`
- `SettingAirPlayAdvancedActivity.java`
- `AirPlayServer.java`
- `CastConflict.java`
- `CastNetworkWatcher.java`

迁移策略：

1. 复用 `airplay/` 的 JNI、UxPlay、RAOP、DNS-SD、音频输出和补丁代码。
2. Media3 统一固定为项目当前使用的 1.10.1。
3. 重新对接当前 `PlaybackService`、`PlaybackActivity` 和 `PlayerManager` 生命周期，不复制旧播放器控制逻辑。
4. AirPlay、DLNA Renderer 和普通播放必须互斥，并在切换时释放 Surface、MediaCodec、EGL、音频输出和回调。
5. 网络切换后分别重建 AirPlay 和 RAOP 的 NSD 注册；重试必须可取消且有退避上限。
6. 仅进入 Leanback APK；mobile APK 不打包 AirPlay 原生模块。

风险：

- 原生模块体积较大，依赖递归 submodule、NDK、CMake、FFmpeg、OpenSSL 和 Oboe。
- 必须核对第三方 NOTICE、GPL 义务和 FairPlay 相关分发风险。
- 需要验证 H.264/H.265 镜像、URL 视频、ALAC/AAC、PIN、异常断连和网络切换。

### 3.2 SMB/WebDAV 网络存储

供体提交：

- `63c08f73` `feat(network): 添加网络存储功能模块`
- `8f98672d` `feat(network): 添加网盘功能及相关备份和配置优化`

主要供体代码：

- `storage/NetworkStorage.java`
- `storage/NetworkStorageStore.java`
- `storage/NetworkPlayResolver.java`
- `storage/SmbClientHelper.java`
- `storage/SmbDiscover.java`
- `storage/WebDavClientHelper.java`
- `player/exo/SmbDataSource.java`
- `NetworkStorageActivity.java`
- `NetworkStorageEditActivity.java`
- `NetworkBrowseActivity.java`

迁移策略：

1. 将网络存储建模、浏览和播放接入当前 VOD data-source/controller 边界。
2. EXO 使用独立的 `SmbDataSource`；MPV 仅在明确支持且凭据不会泄漏时开放对应路径。
3. WebDAV 解析为 HTTP(S) 数据源并复用请求头，但诊断、历史和日志不得包含密码或 Authorization。
4. 网络文件作为独立播放来源，不伪装成普通站点解析结果。
5. 首页网盘入口仅在配置了首页存储时显示。

必须先于合入完成的安全改造：

- 凭据使用 Android Keystore 支持的加密存储，禁止普通 JSON 明文密码。
- HTTP WebDAV 默认拒绝携带 Basic Auth；需要用户显式确认才允许不安全连接。
- SMB 加密失败后不得静默降级；降级需要独立配置和明确提示。
- 网络存储 Activity 设为 `android:exported="false"`。
- 校验 scheme、host、port、路径、share、UUID 和重定向目标。
- 日志和异常只保留错误类型及脱敏 endpoint。
- SMB 发现支持取消、并发上限、超时和实际子网掩码；不固定扫描 `/24`。

### 3.3 DLNA/UPnP 媒体库

供体提交：

- `2d7f664c` `feat(dlna): 集成DLNA媒体库功能，支持浏览和播放网络媒体资源`
- `2cda0009` `fix(dlna): 修复DLNA相关的服务断开和播放状态处理问题`
- `b6b0b735` DLNA 播放状态缓存与上一曲优化

主要供体代码：

- `DlnaMediaManager.java`
- `DlnaPin.java`
- `DlnaPinStore.java`
- `DlnaBrowserService.java`
- `DlnaServerActivity.java`
- `DlnaBrowseActivity.java`

迁移策略：

1. 保留当前 `release` 的 mobile DMC 和 Leanback DMR。
2. 新增独立的 UPnP ContentDirectory 浏览服务，不复用 Renderer 的状态机。
3. 支持设备发现、目录分页、媒体资源选择、收藏和直接播放。
4. 接入当前播放控制器和历史模型。
5. 生命周期采用可观察的多订阅者模型，页面销毁时移除监听，最后订阅者退出时解绑服务。

必须修正：

- 实现 `StartingIndex` / `RequestedCount` 分页，不能只取前 200 项。
- 不使用单个全局 listener。
- 仅接受明确允许的 HTTP(S) 媒体资源；限制标题、URL 和 DIDL 字段长度。
- 收藏和历史不保存认证信息及敏感查询参数。
- 网络切换后重新绑定并重新发现。

### 3.4 投屏设置、冲突处理和网络重绑定

迁移内容：

- DLNA/AirPlay 开关、设备名、端口和网卡选择。
- `CastConflict`：AirPlay、DLNA 和普通播放三者互斥。
- `CastNetworkWatcher`：Wi-Fi/以太网切换后重启发现和注册。
- 异常断连后释放播放器、Surface、Codec、EGL、音频和延迟任务。
- 手机 DLNA 搜索增加防重复点击、搜索中、空结果和失败状态。

网卡识别不能只按 `eth`/`wlan` 名称判断，应优先使用 `ConnectivityManager` 当前网络及 `LinkProperties`。

## 4. 播放器优化处理

### 4.1 保留 release 实现，不迁移旧版本

以下 `fongmi` 提交与当前播放器架构冲突，不能直接 cherry-pick：

- `aeed3a3d`, `f45e4071`, `b54a9a05`, `17ec55f1`
- `804dbffd`, `0a74491f`, `9a115571`, `529b191f`
- `3aa383da`, `ac734b5a`, `19fca8b1`, `6d1feb41`
- `dced9343`, `213fc3de`, `7607d2bd`, `150af540`
- `7803ad3e`, `bdecc5c2`

对应能力以当前 `release` 为准：

- `PlayerEngine`、`ExoPlayerSession` 和 `PlaybackRecoveryPolicy`；
- 用户解码选择与临时自动降级分离；
- 公共 Media3 `DefaultPreloadManager`；
- 播放位置捕获与重建恢复；
- service-aware 生命周期分发；
- EXO/MPV 音视频效果和 passthrough 协调；
- 当前超时、错误恢复及资源释放策略。

### 4.2 逐项语义核对后吸收

以下优化不涉及整体播放器状态机，可在对比当前代码后单独吸收：

- 慢速 HLS master 的等待和取消边界；
- 网络切换后的 cast rebind；
- READY/ENDED/error 时取消过期 timeout/retry；
- MPV 流切换后的 Surface/视频输出恢复；
- 首帧超时只允许有界延长；
- 直播左右键慢速拖动和界面隐藏边界；
- 播放服务退出时释放 native decoder。

任何吸收都必须遵循：

- 不覆盖永久用户设置；
- 不添加无界重试；
- 不重复注册回调；
- 不在旧 PlayerManager 状态机和新 controller/session 中各实现一遍；
- 重建前捕获位置，成功 READY 后统一清理恢复状态。

## 5. 其他优化审计

### 建议吸收（若当前不存在等效实现）

- `History` 使用限制为 2 的安全拆分。
- `Url` 空列表和下标边界。
- JianPian 总容量除零保护。
- Strm、ParseJob 对空响应体处理。
- TVBus 初始化失败后清空无效 core。
- Thunder 明确失败状态和有界超时。
- 本地文件响应创建失败时关闭输入流。
- `CastNetworkWatcher.unregister()` 生命周期清理。

### 已有或可能已有更强实现，先比较再决定

- 本地文件路径安全检查。
- 数据库备份迁移、数量限制和恢复验证。
- 启动时配置刷新。
- QuickJS 生命周期。
- 播放历史恢复。
- 首页焦点和滚动。
- 多语言资源。

## 6. 不采用的做法

- 不执行 `git merge fongmi`。
- 不将 `release` 反向移植到 `fongmi`。
- 不回退 Room schema。
- 不复制旧 EXO/MPV 状态机。
- 不引入私有 Media3/MPV API。
- 不因旧提交存在就覆盖当前已验证的公共 API 实现。

## 7. 实施阶段

### M0：工作区保护

- 保存当前 `release` 未提交变更。
- 建立 `integration/fongmi-port` 临时分支。
- 记录干净基线和生成文件清单。

### M1：小型健壮性修复

- extractor、边界、资源关闭和网络 watcher。
- mobile/Leanback debug 编译及单元/自检。

### M2：SMB/WebDAV

- 安全凭据存储、模型、浏览器和 EXO 数据源。
- 首页入口和播放集成。
- NAS/WebDAV 真机验证。

### M3：DLNA 媒体库

- 发现、分页浏览、收藏、播放和生命周期。
- 与现有 DMC/DMR 共存验证。

### M4：投屏设置和冲突管理

- 设置页、网络重绑定、普通播放暂停/恢复和资源释放。

### M5：AirPlay

- 原生模块、宿主桥接、TV UI、设置和构建系统。
- 镜像、URL 视频、音频、断连和网络切换测试。

### M6：综合验证

- Mobile/Leanback ARM64 debug/release。
- 必要时补 ARMv7/x86_64 编译。
- lintVital、R8、签名、资源压缩和 APK 内容审计。
- 手机与 TV 的 EXO/MPV、字幕、preload、MediaSession、投屏和网络存储回归。
- CPU、PSS、功耗、网络、Codec、Surface 和 native leak 检查。

### M7：收尾

所有迁移和验证通过后才执行：

```bash
git branch -D fongmi
git push origin --delete fongmi
```

删除前确认 `git log release..origin/fongmi` 中每项独有价值均已满足以下之一：

1. 已语义迁移；
2. 当前实现已经覆盖；
3. 明确记录为不采用并说明原因。

## 8. `origin/fongmi` 独有 36 提交处置矩阵

处置含义：**迁移**为按 `release` 新架构重写；**覆盖**为当前实现已有同等或更强能力；**不采用**为明确排除旧架构、回退或仅 CI 历史改动。所有播放器相关提交都未直接 cherry-pick 旧状态机。

| # | 供体提交 | 处置 | 当前证据/理由 |
|---:|---|---|---|
| 1 | `aeed3a3dc` hard decode/recovery | 迁移 | `PlayerEngine`、`PlayerSetting`、`PlaybackRecoveryPolicy` 分离永久设置与临时降级。 |
| 2 | `f45e4071f` repair MPV/EXO | 迁移/覆盖 | 当前 controller/session 恢复、prepared source 与生命周期实现，不复制旧状态机。 |
| 3 | `b54a9a051` 后台/退出释放 MediaCodec | 覆盖 | `PlaybackService` owner 生命周期和 engine release 统一释放。 |
| 4 | `17ec55f16` repair MPV/EXO | 迁移/覆盖 | 播放位置捕获、重建恢复、timeout 清理及公开 API 实现。 |
| 5 | `1bd65e4c6` spider action refresh | 迁移 | `Result.refresh/position`、`SiteViewModel.action` 与 TV/mobile 列表恢复位置。 |
| 6 | `804dbffdc` MPV recovery/wrapped HLS | 迁移 | 公共 MPV 恢复；无 byte-range/URI 属性的有限 HLS 使用白名单、过期会话 `/tsraw` 解包。 |
| 7 | `0a74491fb` repair MPV/EXO | 迁移/覆盖 | 当前 `PlayerManager`/engine/session 边界替代旧实现。 |
| 8 | `9a115571e` stream switch VO | 迁移 | `MpvPlayer` 在换流和 Surface 返回后重绑 VO。 |
| 9 | `529b191f9` app exit release | 覆盖 | service、native decoder、回调与延迟任务均在 stop/release 清理。 |
| 10 | `3aa383da7` native init/packaging | 迁移 | source-build `media3compat`、ABI 打包和 JNI 运行时校验。 |
| 11 | `ac734b5a0` slow HLS master | 迁移 | MPV 首帧/错误等待有界延长，取消过期任务。 |
| 12 | `19fca8b12` live buffering/HLS errors | 迁移 | 直播 data-source/controller 分离及 HLS 错误去抖恢复。 |
| 13 | `6d1feb41c` MediaCodec compatibility | 迁移 | guarded Surface、硬解失败临时迁移，不覆盖用户选择。 |
| 14 | `dced9343c` cache/decode settings | 迁移 | 自适应缓存、scene decode 与公共 preload 管理。 |
| 15 | `2380569c4` guarded Vulkan | 迁移 | build-time capability + 设备 Vulkan 1.2 双重门控。 |
| 16 | `85a762eb3` i18n | 迁移 | Android 多语言资源与 `/locale` Web UI 本地化。 |
| 17 | `80bdfef48` libmpv build update | 迁移 | 固定源码版本、source build、Surface guard 和 Vulkan 构建脚本。 |
| 18 | `65c2cad1a` AirPlay | 迁移 | Leanback-only native/service/playback 集成。 |
| 19 | `32605bdf2` cast 设置与互斥 | 迁移 | 设置、PIN、播放页、冲突管理和资源释放。 |
| 20 | `63c08f737` 网络存储 | 迁移 | SMB/WebDAV 模型、浏览、播放源和安全策略。 |
| 21 | `8f98672d6` 网盘/备份优化 | 迁移/覆盖 | 首页网络存储已迁移；备份沿用 `release` 新实现。 |
| 22 | `2d7f664c6` DLNA 媒体库 | 迁移 | ContentDirectory 发现、分页、收藏、浏览和播放。 |
| 23 | `2cda00096` DLNA 断线处理 | 迁移 | 多监听者、引用计数、断线重连，并等待 binder registry 就绪。 |
| 24 | `b30d3a92b` 网络切换 rebind | 迁移 | `CastNetworkWatcher` 按当前网络重绑，初始 callback 不误重启。 |
| 25 | `53f3fcf23` 本地路径安全 | 迁移 | canonical path/scheme 范围校验与安全响应。 |
| 26 | `8fd49f9dd` extractor 边界 | 迁移 | JianPian/Strm/TVBus/Thunder/ParseJob 空值、容量和超时保护。 |
| 27 | `213fc3dec` decode switch/release | 迁移 | 当前 decode migration 与 service-aware release。 |
| 28 | `7607d2bdf` rebuild traffic | 迁移 | 每 Activity 独立 sampler，播放器重建时重置基线。 |
| 29 | `150af5401` state/error recovery | 迁移 | controller 状态门控与有界恢复，避免 stale callback。 |
| 30 | `322c8c320` home scroll/focus | 迁移 | `HomeGridView` 顶行固定、焦点恢复及鼠标滚轮/拖动阈值。 |
| 31 | `b6b0b7356` DLNA 状态/上一曲 | 迁移/覆盖 | renderer 状态缓存和当前导航 callback。 |
| 32 | `d033c9fab` concurrency/state | 迁移 | `App.post` 原子去重、`Clock` 重入释放、`Sniffer` scheme 优先级。 |
| 33 | `754accbcd` history/traffic state | 迁移/覆盖 | traffic 实例隔离；历史由快照和串行队列写入。 |
| 34 | `dad07b36b` report 5 fixes | 迁移 | DLNA service、播放服务和 VOD 边界修复均按当前架构吸收。 |
| 35 | `7803ad3e0` live slow seek/UI | 迁移 | delayed seek 合并、group 下标保护、EPG/控制层隐藏与 callback 清理。 |
| 36 | `bdecc5c22` stale timeout/retry | 迁移 | READY/START/FILE_LOADED/release 统一取消过期 timeout、retry 和 END_FILE 延迟错误。 |

清单已用 `git log --reverse release..origin/fongmi` 和 `git rev-list --count release..origin/fongmi` 复核，恰为 36 项。额外移植的线路学习、Web UI locale 等改进不冒充供体提交，但作为同批安全优化保留。

## 9. 当前验证状态

截至 2026-09-22：

- 已按上表完成 36 个 `origin/fongmi` 独有提交的逐 SHA 处置，并以 `release` 为唯一基线完成语义迁移。
- 已迁移并加固 SMB/WebDAV、DLNA 媒体库、AirPlay、投屏冲突管理与网络重绑定；凭据采用 Android Keystore/AES-GCM，危险 HTTP/SMB 降级需要显式授权。
- AirPlay ARM64 原生库已用 NDK 29、OpenSSL 3.4.4 固定 SHA-256 构建；Leanback release APK 已确认包含可加载的 ARM64 `libairplay_native.so`。
- 修复了两个仅在 release/R8 下暴露的问题：JNI 回调被混淆，以及 app 使用旧 NDK strip 工具导致 NDK 29 `libc++_shared.so` 无法加载。
- 最新优化头 `9a89e678b` 已重新通过全部四个 ARM64 任务：
  - `assembleMobileArm64_v8aDebug`
  - `assembleLeanbackArm64_v8aDebug`
  - `assembleMobileArm64_v8aRelease`
  - `assembleLeanbackArm64_v8aRelease`
  - release 构建同时通过 R8、资源压缩、签名校验和 `lintVital`。
- ARM64 libmpv 已从源码重建；原始及 APK 内 `libmpv.so` 均声明 `NEEDED libvulkan.so` 且包含 `VK_KHR_surface`，MPV 与 libplacebo Meson 日志均为 `Run-time dependency vulkan found: YES`。构建缓存使用逐 ABI revision marker，防止 ARMv7 误复用 ARM64 结果。
- 手机（API 36）与 TV（API 32）在同一最新 debug 头上均通过 `SelfCheckActivity: passed=108 failed=0`；新增覆盖 action refresh、线路质量哈希存储及 PNG-TS 检测/解包。
- 手机和 TV 最新 release APK 均安装并冷启动成功，无 fatal/`UnsatisfiedLinkError`；TV 上 AirPlay 监听 7000，`_raop._tcp` 与 `_airplay._tcp` 均注册成功。
- 首页“媒体库”崩溃已定位为 UPnP service binder 已连接但 registry/control point 尚未就绪。`DlnaMediaManager` 现进行空安全延迟 attach，并在搜索/枚举/解绑路径保护未就绪状态；TV debug 与 release 均实测进入 `DlnaServerActivity` 并保持 resumed，无崩溃。
- 打开媒体库后关闭 App 的崩溃已定位：jUPnP 3.0.4 `AndroidUpnpServiceImpl` 只构造 `UpnpServiceImpl`，未自动调用 `startup()`；router 为 null 时父类 `onDestroy()` 会在 `AndroidRouter.unregisterBroadcastReceiver()` 空指针。当前修复由 `DlnaBrowserService.onCreate()` 同步启动 UPnP，并以 `upnpStarted` 区分完整/部分初始化；只有该已知空指针走手动 registry/config/router 清理，不再用 `catch (Throwable)` 隐藏 VM 错误。`DLNARendererService` 对“设置关闭后 startup 前 stopSelf”及启动失败采用同一策略。
- 剩余 fongmi 播放器优化已吸收：`MpvPlayer` 合入 Surface settle、`video-reload`、embed VO 失败降级、黑屏 watchdog、`HWDEC_HARD=mediacodec`（规避 Rockchip 10-bit HEVC copy-back 绿屏），并保留 fork 的音量增益/af/vf/均衡器 API。审计时否决了“MPV 锁持有 30 秒后强制解锁”：native bridge 为进程级单例，慢速 destroy 期间解锁会允许两个 Java player 同时控制同一 handle；改为初始化失败时主动 destroy，并始终在 finally 释放锁。
- 播放场景已显式贯穿 `PlayerManager → PlayerEngineFactory → Exo/MPV engine/session`：直播的低延迟 LoadControl、MPV demux cache/reconnect/RTSP 策略真正生效；短 VOD 不再因“时长小于一分钟”被误判为直播。DASH/MPD、DRM、SMB 强制走 Exo，配置中的 `playerType=1` 可临时选择 MPV但不覆盖用户持久设置。
- HTTP/cache 安全边界进一步收紧：Exo 为每个 MediaItem 创建独立 header factory，带 Authorization/Cookie 的媒体绕过共享缓存并禁止预加载；系统 HTTP 实现禁止跨协议重定向。MPV 切换条目时恢复 `mpv.conf` 中的 UA/Referer/header，校验 header name，并对敏感 header 关闭磁盘缓存。
- 其他修复：MPV stop 取消延迟 END_FILE 错误、READY 取消 Exo 延迟重试、音频焦点恢复保留 volume gain、黑屏 watchdog 跳过纯音频、绝对本地路径仅允许位于共享存储根内；`PlayerSetting` 增加 buffer/http/liveLatency，缓冲设置同时接入 Exo 与 MPV；引入 `scripts/release_playback_regress.sh`。
- 本轮 TV 回归（leanback arm64 debug）：`SelfCheckActivity` 108/108；`MpvSmokeActivity` HLS `RENDERED_FIRST_FRAME`，`c2.rk.avc.decoder` + `Using hardware decoding (mediacodec)`；`ExoSmokeActivity` DASH `RESULT ok=true` decoder=`c2.rk.avc.decoder`；媒体库进入后 force-stop 无 crash。
- 设置页已补「缓冲时间 / HTTP 方式 / 直播延迟」双端入口，并接入 Exo `DefaultLoadControl` 与 MPV demuxer 缓存。
- `Path.resolveUnderRoot`、`Local` 路径校验、`FileUtil.copyAtomically` 与根目录 `LICENSE` 已齐备。

## 10. 运行问题分析（2026-09-22，待改代码）

### 10.1 SMB 加 1 个出现 2 个文件夹

**已修复（2026-09-22）：**

- `NetworkStorageStore.save()` 增加 `isValid()` 校验；按 id **或规范化业务键** `(type, host, port, share, path)` 去重；host/type/SMB share 比较忽略大小写，path 经安全规范化。
- 命中旧条目时复用旧 `id` 再覆盖（播放 URL / 凭据 key 稳定），并清除旧版本已经留下的其余重复项、孤立凭据，同时迁移首页存储 id。
- `NetworkStorageEditActivity` 编辑已有条目本就通过 `find(id)` 复用 id；新建同端点条目现由 `save()` 合并。

根因记录：

- `NetworkStorage.create()` 每次 `UUID.randomUUID()` 生成新 id。
- 旧 `save()` 仅按 `id` 覆盖，否则 `list.add(item)`。

### 10.2 媒体库很久才扫出 UPNP-iptv

**已优化（2026-09-22）：**

- `init()` 进页面立即 `search()`，并 `scheduleRescan()` 在 3s / 8s 各补搜一次（有界，不无限重试）；最后一个订阅者退出时取消两个固定 Runnable，避免重新进入页面后叠加旧扫描。
- attach 重试缩短为 `10 × 150ms`（原 `20 × 250ms = 5s`）。
- attach 完成后原有 `search()` 保留，形成「立即 + 就绪 + 补搜」三段。
- ContentDirectory 分页改用 UPnP `BrowseResult.NumberReturned/TotalMatches` 推进，不再错误使用安全过滤后的 `page.size()`；原始扫描下标与返回条目均限制在 2000，避免全是无效资源时无界翻页。

根因记录：

- attach 最多等 5s 才发第一次 `search`。
- `STAllHeader` 全量搜索；IPTV/机顶盒 UPnP 栈回应常 2–10s。
- 只收 `MediaServer` + `ContentDirectory`，描述/服务列表慢的更晚入库。
- 列表只读 registry，回调前 UI 空白，体感像卡住。

### 10.3 备份原子性

计划对齐 fongmi `dca422818`：`BackupManager` 原子替换、恢复失败回滚、UI 状态反馈。
- TV 本地 server 的 `/locale` 返回 `zh-CN`；无效 `/tsraw` session 返回 `404 session expired`，不会形成匿名开放代理。
- OkHttp 默认重定向实现会在跨 origin 时移除 `Authorization`；播放器使用同一 OkHttp 重定向链，未增加会跨 origin 重注入该请求头的自定义逻辑。
- 尚未完成：真实 SMB/WebDAV 服务器、DLNA DMC/DMR/ContentDirectory、AirPlay 音频/视频/镜像/PIN/断连/换网的完整全链路回归，以及 ARMv7 构建；x86_64 已按项目基线移除，不再作为合入门禁。
- 尚未删除 `origin/fongmi`；必须等上述协议回归后再合并 `release` 并删除远端供体分支。

以下 APK 是 `9a89e678b` 的上一轮已验证产物，**不包含本节后续的 DLNA shutdown、场景化缓存和 header 隔离修复**；新批次完成构建/设备回归后必须替换：

```text
app-mobile-fongmi-port-debug.apk    178534457  sha256 fa876b3eaf810a899da7a49b7e170c0a4631e92ba3f25ddac515c3c4ceca8241
app-leanback-fongmi-port-debug.apk  214953052  sha256 36a61bb56fdf6b970b4ecdf6e12c456e0d4769be6315465827b4cdac0e6e746e
app-mobile-fongmi-port-release.apk   81881144  sha256 4fd20bf6c86bf6f5dd7049c632531588da40d83392b6175be0ec7c9ac1f35d0f
app-leanback-fongmi-port-release.apk 90674135  sha256 2e5ef82775f3d6afbf4ff189ec7bebb7b6bfc98c2a07d4cf9d058b5fef33d88c
```

因此，本文档可作为已列明构建、自检、媒体库回归和冷启动结果的证据，但不得据此宣称全部网络协议或 AirPlay 媒体路径已完成端到端验证。
