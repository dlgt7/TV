# 上游移植复核报告（2026-09-20）

> 复核对象：本轮从上游 `FongMi/TV` 移植进 fork 的未提交改动（31 个文件）。
> 复核方式：静态审计 + 构建机编译 + 手机/电视真机实测 + 日志与资源采样。
> 结论：**移植主体可用**，实测未发现崩溃；定位并修复 3 个真实缺陷。
>
> 说明：本文件是独立复核记录，`UPSTREAM-GAP-AUDIT-2026-09-20.md` 中「已补齐」的声明我逐项复核，
> 成立与不成立的部分见第 5 节。

---

## 1. 复核范围

本轮未提交改动（工作区，基于 `cd31ba4e0`）：

| 模块 | 文件 |
| --- | --- |
| 预载 | `media3compat/.../DiskPreloadManager.java`、`PreCache.java`、`PreloadPolicy.java`、`PreloadDiagnostics.java`、`PreloadSetting.java` |
| EXO 增益 | `ExoVolumeGain.java`（本轮调用方改动）、`PlayerEngine`/`PlayerManager` |
| 错误恢复 | `PlaybackRecoveryPolicy.java`、`ExoPlayerEngine.java` |
| 字幕字体 | `FontFamilyParser.java`、`ExternalFont.java`、`SubtitleSetting.java`、`MpvUtil.java` |
| UI | `PreloadDiagnosticsDialog.java`、`ExternalFontDialog.java`、`SubtitleDialog.java`、手机/电视设置页与布局 |
| 测试工具 | `PreloadSmokeActivity`、`SelfCheckActivity`（debug 源集） |

---

## 2. 构建与真机验证结果

### 2.1 构建

构建机 `zyq@192.168.42.153`，独立 worktree `.../FongMi/TV-verify`（未触碰主树在途改动）。

```
:app:assembleMobileArm64_v8aRelease
:app:assembleLeanbackArm64_v8aRelease
BUILD SUCCESSFUL in 2m 11s  (262 actionable tasks)
```

含 `lintVital`、R8、签名与打包，**lintVital 无 NewApi 报错**（这一点在第 5 节有实际意义）。

> 追加：修完 3.5 的解码持久化缺陷后**再次全量构建**（`... assembleMobileArm64_v8aRelease assembleLeanbackArm64_v8aRelease`）
> → `BUILD SUCCESSFUL in 2m 24s`，262 tasks / 18 executed；两个 APK（各约 164 MB）
> 已 `install -r` 到两台设备，冷启动与设置页、直播播放均正常，`logcat -b crash` 为空。

### 2.2 设备

| 设备 | ADB | API | 型号 | 包 |
| --- | --- | --- | --- | --- |
| 手机 | `192.168.1.202:5555` | **36**（Android 16） | Redmi Note 8 (ginkgo) | mobile-arm64_v8a |
| 电视盒子 | `home-rk:5555` | **32**（Android 12） | rk3588 | leanback-arm64_v8a |

两台均 `install -r` 成功，冷启动无崩溃。

手机解锁方法（本轮实测有效）：`svc power stayon true` → `input keyevent KEYCODE_WAKEUP` → `input keyevent 82` → `wm dismiss-keyguard`。
注意手机锁屏会很快重新出现，**必须在同一条命令里完成「解锁 → 启动 → 抓取」**。

### 2.3 功能实测

| 项 | 手机 (EXO) | 电视 (MPV) |
| --- | --- | --- |
| 冷启动 TotalTime | 1223 ms | 171–177 ms |
| 首页 / 动态壁纸 | 正常 | 正常 |
| 设置 → 播放设置 | 正常（引擎 EXO） | 正常（引擎 MPV） |
| 设置 → 预载设置 | 正常 | 正常，新条目齐全 |
| **「按流量计费网络也预载」** | 存在 | 存在，可切换 |
| **「预载诊断」弹窗** | 存在 | 内容正确，见下 |
| **「字幕字体」** | 显示 `DejaVuSans` | 存在 |
| 直播播放 | PLAYING，`c2.android.avc.decoder` | PLAYING，`c2.rk.avc.decoder` |

电视端「预载诊断」实测输出（与 `UPSTREAM-GAP-AUDIT` 记录一致）：

```
policy=allowed (allowed) / meteredAllowed=false / duration=120s / threads=1
started=0 completed=0 cancelled=0 failed=0 skipped=0 / last=idle
```

> `threads=1` 且预载设置页**不显示「预载线程」**是正确行为：`preloadThread` 仅在 `!PlayerSetting.isMpv()` 时可见，电视当前引擎是 MPV。

### 2.4 资源占用（release 包，实测）

| 场景 | CPU | PSS | RES |
| --- | --- | --- | --- |
| 电视 MPV 零拷贝直播（**1080p**） | 40–52% | 216 MB | ~366 MB |
| 手机 EXO 直播（软解） | 20–50%（波动大） | 214 MB | ~338 MB |

电视播放日志确认走的是**性能硬解/零拷贝**路径：

```
[vo/mediacodec_embed] Set property: vo="mediacodec_embed"
Set property: hwdec="mediacodec"
MediaCodec started successfully: codec = c2.rk.avc.decoder
Using hardware decoding (mediacodec)
Decoder format: 1920x1080 mediacodec
```

> 电视 CPU 比审计文档记录的 32–44% 略高，原因是**分辨率不同**：本次测的是 1080p 频道，
> 而 MPV 自编译内核为 `mpv v0.41.0-895-g8c67647b5-dirty` / FFmpeg `n8.0.1` / libplacebo `v7.371.0`。

### 2.5 播放期间无异常

- 手机、电视 `logcat` 均**无** `FATAL` / `AndroidRuntime` 崩溃 / `PlaybackException` / `VerifyError` / `NoSuchMethodError`。
- 电视稳定播放期间无持续 `underrun` / `desync` / `buffering`。
- 唯一异常来自**站源自身**（与本轮改动无关）：

```
java.lang.IndexOutOfBoundsException: Index: 0, Size: 0
  at com.github.catvod.spider.Libvio.homeContent(Unknown Source:499)
```

即 `Libvio` 站点接口返回空列表导致首页退化为空态，属源站问题。

---

## 3. 发现并修复的缺陷

### 3.1 【已修】预载诊断弹窗在电视上按钮不可达（遥控无法操作）

- **现象**：电视上用遥控器打开「预载诊断」后，焦点被弹窗正文吞掉，`DPAD_DOWN/LEFT/RIGHT` 都无法移到
  「确定 / 重置」按钮，只能按 BACK 关闭。
- **根因**：`PreloadDiagnosticsDialog` 对正文调用了 `setTextIsSelectable(true)`。该调用会让 TextView 变为可聚焦，
  在无触摸屏设备上优先抢占并锁住 D-pad 焦点。
- **修复过程中的第二个坑（重要）**：第一版修复用
  `PackageManager.hasSystemFeature(FEATURE_TOUCHSCREEN)` 判断，**在真机上无效** ——
  这台 rk3588 盒子实测 `pm list features` 会返回
  `android.hardware.touchscreen` / `.multitouch` / `.multitouch.distinct`（廉价盒子常见的过度声明），
  但它并没有触摸屏。最终改为：**`FEATURE_TOUCHSCREEN` 且 `UiModeManager.getCurrentModeType() != UI_MODE_TYPE_TELEVISION`**
  才算触摸驱动；同时非触摸时对 `ScrollView` 设置 `setFocusable(false)` +
  `FOCUS_BLOCK_DESCENDANTS`（ScrollView 自身也可能抢焦点，只把 TextView 改成不可聚焦还不够）。
- **实测验证（电视 rk3588）**：
  - 修复前：`open 预载诊断: focus='policy=allowed ...'`（焦点在正文）
  - 修复后：`open 预载诊断: focus='重置 | 确定'`，且按 LEFT 后 `FOCUSED: ['重置']`

### 3.2 【已修】「重置」按钮在回调里重新 `show()` 弹窗

- **现象/风险**：`setNeutralButton` 的回调中执行 `PreloadDiagnostics.reset(); show(requireActivity());`。
  在弹窗自身正在 dismiss 的过程中发起新的 fragment 事务，容易触发
  `FragmentManager is already executing transactions` / `IllegalStateException`；
  且原弹窗可能是用 `childFragmentManager` 显示的，重建时却切到 activity 的 FragmentManager，层级不一致。
- **修复**：`setNeutralButton(..., null)` 建好弹窗后，用 `setOnShowListener` 取 `BUTTON_NEUTRAL` 覆盖其
  `OnClickListener`，点击时只做 `reset()` + `message.setText(buildReport())`，**弹窗保持打开**。
  这样既保留了「重置后继续观察计数」的原意，又完全不触发 fragment 事务。
  （注意 `Dialog` 没有 `getButton()`，变量需声明为 `androidx.appcompat.app.AlertDialog`。）
- **实测验证（电视 rk3588）**：打开弹窗 → 按「重置」→ 弹窗**仍在**（`dialog open: True`），
  焦点停在 `重置` 上，`logcat` 无 `FATAL` / `IllegalStateException`。

### 3.3 【已修】字幕字体弹窗在 UI 线程重复解析全部字体 3 次

- **现象**：`ExternalFontDialog` 的 `buildLabels()`、`checkedIndex()`、`onSelect()` **各自**调用一次
  `ExternalFont.getAll()`。而 `getAll()` → `getEntries()` → `FontFamilyParser.readFaces()` 会把**每个**字体文件
  整份读入内存（单文件上限 32 MiB）并解析 `name` / `fvar` 表。三次调用都发生在 UI 线程（对话框创建与点击回调）。
- **影响**：当前字体目录只有 1 个 `DejaVuSans.ttf`（743 KB），体感无异常；一旦用户导入多款字体或一个大 TTC，
  单次打开弹窗就是数倍的整文件读取 + 解析，构成明显卡顿甚至 ANR 风险。
- **修复**：新增 `fonts()` 惰性缓存，一次枚举复用到 `buildLabels` / `checkedIndex` / `onSelect`。

### 3.4 【已修】字体导入时文件名为空会生成 `null.ttf`

- **根因**：`ExternalFont.importFrom()` 中若 `FileUtil.getDisplayName(uri)` 返回 `null`
  （部分文件选择器对 content URI 给不出名字），`isSupportedName(null)` 为 false，于是执行
  `display = display + ".ttf"` → 字符串拼接出 `"null.ttf"`。
- **修复**：显式判空，回退为基于 URI 哈希的 `font-<md5>.ttf`（与已有的 `sanitizeName()` 兜底风格一致）。

### 3.5 【已修】自动解码回退会把「软解」写进用户偏好（与已知的「软解粘住」问题同源）

> 这一条是第 6 节原 P1「EXO 直播落在软解」的**机制侧**修复。注意：本节结论比初稿收窄——
> 真机上的 `0` 值**不一定是**这条路径写出来的（见下方「证据与边界」），但这条路径确实是让软解
> **持续粘住**的残余机制，且与作者此前自己的判断完全一致。

- **代码缺陷（确定）**：`PlayerManager.toggleDecode()` 同时被两个语义完全不同的调用方使用：
  - 用户在控制栏点「解码」（`PlaybackAction.toggleDecode`）——应当持久化；
  - 硬解失败后的自动回退（`handleDecodeError` ← `onPlayerError` ←
    `PlaybackRecoveryPolicy.decide()` 返回 `SWITCH_DECODE`）——**不应当**持久化。
  但该方法里 `PlayerSetting.putDecode(liveMode, getEngine(), decode)` 是**无条件**执行的。
  于是一次**瞬时**硬解失败（硬件解码器被其它 App 短暂占用、或某一路流本身不被硬解支持）
  就会被当作长期偏好写入 `live_exo_decode` / `vod_exo_decode`。
- **为什么这是错的**：解码能力是**逐设备、逐编解码器、逐码流**的，不是「EXO 直播」这个场景的
  全局属性；用单条流的失败覆盖整个场景的偏好，语义上不成立。而且 `ExoUtil` 已经
  `setEnableDecoderFallback(true)`，会话内的编解码器级回退由 Media3 自己负责，
  这一层手工回退只需要是**运行时**的。
- **修复**：拆成两个入口，落点唯一——
  - `toggleDecode()` → `applyDecodeToggle(true)`：用户主动切换，**保留持久化**（与原代码逐字节等价）；
  - `toggleDecodeTransient()` → `applyDecodeToggle(false)`：错误自动回退，**只在内存里改 `decode`，不写 `Prefers`**。
  已确认 `engine.setDecode()` 只改运行时字段（`ExoPlayerEngine.setDecode` 仅赋值 `this.decode`），
  持久化点全项目只有 `PlayerManager` 这一处，故该改动完整封闭。
- **不会引入死循环**：`retry` 仍为原语义，`++retry > 1` 即上报错误，且 `reset()` 会清零；
  回退后若再次失败直接走 `onError`。

#### 证据与边界（真机实测）

1. **软解确实粘在偏好里**（此前只有日志旁证，这次拿到了直接证据）。手机端
   `设置 → 备份` 产出的 `/sdcard/TV/backup/2026-09-20.tv` 是 **gzip 后的全量偏好 JSON**
   （`Backup.setPrefers(Prefers.getPrefers().getAll())`），adb 可直接取回解压。其中：

   ```
   live_exo_decode   = 0      vod_exo_decode   = 0
   live_mpv_decode   = 2      vod_mpv_decode   = 2
   ```

   同时 `播放设置 → 解码设置` 里用户可见的「视频软解」是**关**，即软解并非用户显式勾选，
   而是经由场景级 `*_exo_decode` 生效。
2. **不是会话级回退**：清空 logcat 后播放央视/湖南卫视，全量日志里出现的解码器**只有**
   `c2.android.avc.decoder`（24 次）与 `c2.android.hevc.decoder`（21 次），
   **从未尝试过任何硬件解码器**（该机为高通，硬解名形如 `c2.qti.*`），也没有
   `DecoderInitializationException` / init failed。若属「硬解失败→会话内回退」，必先看到硬解尝试失败。
   故可判定走的是 `MediaCodecSelector.PREFER_SOFTWARE`，即 `decode == 0`。
3. **`0` 的来源收窄**：作者本人在提交 `aeed3a3dc`（2026-07-27，
   "Prefer hard decode and harden live/VOD playback recovery."）中**已经识别过这一类问题**，
   迁移注释写得很明确：

   > `// Soft must not stick as a scene default; hard-first with soft as last fallback only.`

   但该迁移（`decode_defaults_migrated_v3`）只覆盖了 **MPV** 三个键
   （`mpv_decode` / `live_mpv_decode` / `vod_mpv_decode`，故现值均为 2），
   **没有覆盖 EXO 的两个场景键**——与实测中 EXO 键仍为 0 完全吻合。
   该迁移代码在**当前源码里已经不存在**（`git grep decode_defaults` 无结果），
   因此这两个 `0` 不会被自动修复。
4. **因此本节的边界**：无法证明真机上这两个 `0` 就是由 `handleDecodeError` 写出的
   （它也可能是更早某次手动切换或旧版本遗留）。可以确定的是：
   （a）EXO 场景键确实粘在软解；（b）`toggleDecode()` 的无条件持久化是**唯一**能写出该键的代码路径
   （全项目仅此一处 `putDecode`），且其中一条调用链就是自动回退；（c）已按作者既定策略修复该机制。
5. **未完成项**：在真机 UI 上点一次「解码」按钮做 `0 → 1` 的往返验证未完成——手机版直播页
   控制栏需「播放中双击」才会出现（`onDoubleTap` 内 `isPlaying()` 为真才 `showControl()`），
   而本次所有内置直播源都返回 `Bad HTTP Status` / 无码率，拿不到稳定播放态；
   且该界面 `uiautomator dump` 偶发返回空树（Android 16 已知问题）。
   用户主动切换的持久化路径与原代码**逐字节等价**，风险极低，但此项按「未验证」记录。
   若要复测：可用 `other/tools/phone_nav.py` + 一个可播放源，切换后触发一次
   `HomeActivity.onDestroy()`（其内会调 `BackupManager.backup()`）再取回备份比对。

---

## 4. 复核通过、未发现问题的部分

以下是我重点怀疑但**核查后确认可用**的点，记录证据以免后续重复排查。

### 4.1 `Math.clamp` 的 API 兼容性 —— 已确认安全（重要）

- 静态证据：`android-37.0/data/api-versions.xml` 中
  `clamp(DDD)D`、`clamp(FFF)F`、`clamp(JII)I`、`clamp(JJJ)J` **全部 `since="35"`**；
  而项目 `minSdk = 24`，电视盒子是 **API 32**。
- 进一步证据：拉取电视的 `core-oj.jar` → `classes.dex`，其中 **`clamp` 出现 0 次**
  （对照 `toIntExact`、`multiplyHigh` 均存在），确认 API 32 的 `java.lang.Math` 确实没有 `clamp`。
- 但用 `dexdump` 扫描**实际产出的 APK**：

```
classes.dex / classes2.dex / classes3.dex  →  Math;->clamp 调用点: 0 / 0 / 0
```

- 结论：编译链路（R8/D8 + `coreLibraryDesugaring`）已经把全部 `Math.clamp` 调用重写掉，
  最终 APK 里不存在对 `java.lang.Math.clamp` 的调用，**API 24–34 设备上不会抛 `NoSuchMethodError`**。
- 实证：电视（API 32）上「播放设置」正常渲染出 `播放引擎 = MPV`
  （该值经 `PlayerSetting.getEngine()` → `Math.clamp(JII)I` 读取），未崩溃。

> 这是个「看起来一定会炸、实际被工具链兜住了」的陷阱。**如果将来有人改 R8 配置或升级 desugar 库，
> 需要重新验证这一条**，因为源码里仍有约 120 处 `Math.clamp`。

### 4.2 `DiskPreloadManager` 已移除 media3 内部类依赖

复核时发现初版直接调用包私有 `PreCacheHelper`，与「用公开 API 替代私有 AAR」的目标不一致。最终实现已改为
Media3 1.10.1 的公开 `DefaultPreloadManager`、`PreloadManagerListener` 与
`PreloadStatus.specifiedRangeCached(startPositionMs, durationMs)`；缓存仍由应用持有的共享 `SimpleCache` 提供。

- manager 释放只结束 preload source，不释放共享 cache，因此已下载 span 仍由 LRU 管理并可供正常播放读取。
- 公共 API 版在手机真机重新跑通 forward / reverse / retained / cancelled / failure：前三项命中，取消和 404 错误均产生正确且有界的终态。

### 4.3 其余复核项

| 项 | 结论 |
| --- | --- |
| `DiskPreloadManager.release()` 幂等性、保留已下载 span、淘汰交给 `SimpleCache` LRU | 逻辑自洽；`released` 标记防止释放后回调 |
| `PreCache` 生命周期（`start`/`preload`/`clearPreload`/`stop`/`release`） | 与 `PlayerManager` 的 `pendingPreload` 交接一致，无泄漏路径 |
| 预载缓存 key 一致性 | 播放侧 `CacheDataSource` 与诊断侧 `PreloadDiagnostics.cachedBytes()` 都未显式设置 `CacheKeyFactory`，同用 `CacheKeyFactory.DEFAULT`，key 对齐 |
| `PlaybackRecoveryPolicy` 有界重试 | `MAX_ATTEMPTS=2`，解析类错误不再无界重试；瞬时网络错误改为退避重试；`STATE_READY` 时 `resetErrorBudget()` 复位预算 |
| `ExoVolumeGain` | `setVolume` 仅用于 ≤1.0 的衰减，>1.0 交给 `LoudnessEnhancer`；`toMillibels(1.5) = 352 mB ≈ 3.52 dB` 换算正确；audio session 变化时重建、释放时回收 |
| `FontFamilyParser` 边界检查 | `name` / `fvar` 表偏移、长度、`lookup` 越界均有 `contains()` 校验；TTC face 数、table 数、实例数均设上限 |
| `SubtitleSetting` 字体三元组持久化 | path + faceIndex + family 保存/读取往返一致（与自检 Activity 的结论吻合） |
| `PlaybackRecoveryPolicy.retryDelayMs` | 退避 500→1000→2000→(封顶 3000) 正确；但 `MAX_ATTEMPTS=2` 使 attempt 只到 1，**3000 ms 封顶实际是死代码**，注释里的「封顶」表述会误导，建议改注释（未改代码） |

---

## 5. 与 `UPSTREAM-GAP-AUDIT-2026-09-20.md` 的差异

复核后修正了该文档中的两处（**已在文档内就地更正**）：

1. 初版实际使用包私有 `PreCacheHelper`；最终已按目标改成公开 `DefaultPreloadManager`，并重新完成编译和预载 E2E。
2. **手机端「未测」的结论本次已补齐**：解锁后可正常进入设置、预载设置、字幕字体，
   并完成 EXO 直播播放与资源采样（见 2.3 / 2.4）。

其余声明（电视端设置页、预载诊断弹窗内容、Exo/MPV 三模式、暂停单图层、资源量级）**复核后成立**。

---

## 6. 后续可移植 / 可改进建议

按性价比排序：

| 优先级 | 项 | 说明 |
| --- | --- | --- |
| ~~P1~~ 已完成 | **验证预载实际命中** | 本地 Range HTTP + `adb reverse` 已跑通 forward / reverse / retained / cancelled / failure；命中、保留、取消、404 错误与清理均符合预期 |
| ~~P1~~ **已修（机制）** | ~~**EXO 直播落在软解**~~ → 见 3.5 | 机制已修：`toggleDecode()` 把「错误自动回退」与「用户主动切换」混在一起且都持久化，已拆成持久化 / 非持久化两个入口。真机上 `live_exo_decode`/`vod_exo_decode` **确实为 0**（备份 JSON 直接读到），但该 `0` 更可能来自 `aeed3a3dc` 那次只覆盖 MPV 键的迁移 + 历史遗留，不能断定由本缺陷写出 |
| P2 | **存量软解偏好未修复** | 当前源码已无 `decode_defaults_migrated*` 迁移，故设备上已粘住的 `*_exo_decode = 0` **不会自动恢复**。若确认想要硬解优先（与 `aeed3a3dc` 的 "Prefer hard decode" 一致），可照该提交的既有策略补一个只覆盖 EXO 两个场景键、且仅在值 `== 0` 时重置的一次性迁移（需换新的 marker key，如 `decode_defaults_migrated_v4`）。**这会覆盖用户显式选择的软解，属偏好改写，未擅自实施** |
| P2 | 上游 EQ / 响度归一化 | 依赖私有 Media3/MPV AAR 扩展，CPU/耗电风险高；建议先做基准测试再拆「音频效果」「视频效果」两个里程碑 |
| ~~P2~~ 部分完成 | `ExoPlayerSession` / `ExoSubtitleController` 拆分 | 生命周期已抽为 `ExoPlayerSession`；字幕控制器因上游依赖私有 libass API，后续只能围绕公开 `SubtitleView` 渐进拆分 |
| P3 | media3 升级防护 | 预载现仅用公开 API；升级时仍需复跑 duration/start offset、取消保留与 HLS/MP4 命中测试 |
| P3 | `Math.clamp` 收口 | 见 4.1，可考虑统一改为自有 `MathUtil.clamp`，去掉对 R8 重写能力的隐式依赖 |

---

## 7. 本轮新增的工具与环境记录

| 文件 | 用途 |
| --- | --- |
| `other/tools/build-tv.sh`（新） | 构建机 Gradle 包装脚本。**原因**：直接在 `ssh` 命令里内联 `JAVA_HOME/ANDROID_HOME/PATH` 会被本地 shell 提前展开 `$PATH`，导致远端语法错误；脚本化后彻底规避 |
| `other/tools/tv_nav.py`（新） | Leanback 遥控导航：读 `uiautomator dump` 取焦点、按 D-pad。**关键教训**：电视上焦点节点通常是容器，标签在子节点里，必须取**子树文本**才能判断焦点落在哪一项 |
| `other/tools/phone_nav.py`（新） | 手机（触屏）导航：`texts` / `tap "<标签>"` / `tapid <id>` / `dump`。**关键教训**：匹配必须取**最具体**的节点——整屏是一个容器、其子树包含所有标签，取「第一个命中」会点在全屏中心（实测点飞了）。实现上按 `(clickable, 面积)` 排序取最小者。设备序列号可用环境变量 `PHONE_SERIAL` 覆盖 |

**排查技巧（本轮新发现，很好用）——用应用自身的「备份」当只读偏好检查器**

无法 root、release 包又不可 `run-as` 时，`/data/data/<pkg>/shared_prefs/` 读不到，但：

- `Backup.setPrefers(Prefers.getPrefers().getAll())` 会把**全量偏好**写进备份；
- 备份落在**外部存储**：`Path.tv()/backup/YYYY-MM-DD.tv`，即 `/sdcard/TV/backup/*.tv`，adb 可直接 `pull`；
- 内容是 gzip 压缩的 JSON，`gzip.open(...)` + `json.loads` 即可，无需任何应用权限；
- 触发时机：`HomeActivity.onDestroy()` 会调用 `BackupManager.backup()`，也可在 `设置 → 备份` 手动触发。

于是「偏好当前到底是什么值」可以**被精确观测**，而不是靠日志猜。本条正是 3.5 定位的关键手段。
（注意：同包名的历史版本会共享同一份 shared_prefs，因此备份里可能出现当前源码并不存在的键——
这本身就是判定「该值来自旧版本」的有效线索。）

其它实测踩坑：

- MSYS 的 `/tmp` 与 Windows `tar.exe` 不互通，打包请用仓库内相对路径。
- 每个 Bash 调用有独立临时目录，`cat > /tmp/x` 与随后的命令**不在同一命令里就取不到**。
- `scp` 的目标**要写完整文件名**（`host:/tmp/x.tgz`），写成目录形式（`host:/tmp/`）出现过上传后远端找不到的情况。
- 构建机 `:app:package*Release` 偶发在增量构建中因 `IncrementalSplitterRunnable` 失败，重跑该任务或重跑 assemble 即恢复
  （不是磁盘满，`/data` 尚有约 99G）。
- 电视端 `SettingPreloadActivity` 等设置页**未 exported**，无法 `am start`，必须走 D-pad；
  但 `LiveActivity` / `HomeActivity` 可以 `am start` 直达。
- 手机 `/proc/<pid>/io` 不可读（系统限制），无法用 `rchar` 量化 UI 线程 I/O；字体弹窗的性能问题因此按代码判定。
- 手机 uiautomator dump 在 Android 16 上偶尔返回空文本，此时用 `screencap` 截图确认最可靠。

---

**最终复核结论**：本轮移植已完成公开 API 收口、构建与双端回归。预载从包私有 `PreCacheHelper` 改为公开
`DefaultPreloadManager` 后，forward / reverse / retained / cancelled / failure 五场景全部 PASS；`SelfCheckActivity` 为 **39/39 PASS**。
手机 EXO（含 `Loudness Enhancer`）与电视 MPV mediacodec 硬解均正常，未见崩溃或链接错误。
此外，字体文件复制、校验和元数据解析已移到后台 `Task`，避免最大 32 MiB 导入阻塞 UI。
尚未自动化的只剩字幕字体的主观观感，以及依赖私有 libass API 的完整 `ExoSubtitleController` 拆分。

## 8. 第二阶段迁移补充（2026-09-21）

- 已确认本地文件服务器/导入/文件系统加固、备份迁移、QuickJS 生命周期、历史恢复、artwork、DoH 校验与延迟初始化提交均为当前 `HEAD` 的祖先；未重复覆盖。
- 补齐 `PlaybackActivity` 生命周期分发：依赖播放服务的 LiveData 结果会在 Activity 至少为 `STARTED` 且服务已绑定后再投递；重定向及退出时即使 `MediaController` 尚未连接也会暂停底层播放器。
- 新增 `LiveDataSource`、`VodDataSource` 边界，将 URL/detail/player/preload/search 加载从 Activity host 中移出；保留 fork 的磁盘预载缓存、反向播放和标题更新行为。
- QuickJS proxy 参数编码改用 Android `Uri.encode`，避免已废弃 `URLEncoder.encode(String)` 的平台默认字符集行为。
- 新增 `SecondarySubtitleTimeline` 与 `SecondarySubtitleOverlay`：使用公开 Media3 `DefaultSubtitleParserFactory` 解析独立外部字幕，通过第二个 `SubtitleView` 按播放位置渲染；手机与电视字幕轨弹窗均可选择第二字幕，长按按钮可清除。该实现不伪造私有 libass/双 TextRenderer 能力，复杂 ASS 排版仍以公开解析器能力为限。
- 新增 `PlaybackCapabilities` 显式能力契约：音量增益和公开 API 第二字幕对 EXO/MPV 可用；高成本 EQ/声道混音/归一化/限幅及视频 shader 明确标记为不支持，保持独立项目，不在未建立性能基线时默认启用。

验证结果：

- Mobile/Leanback arm64 debug、release 均构建成功；release 包含 lintVital、R8、签名及打包。
- 手机与电视 `SelfCheckActivity`：**52/52 PASS**，覆盖第二字幕 SRT 时间轴与公开能力契约。
- `DualSubtitleSmokeActivity` 在手机 EXO、电视 MPV 均为 `passed=true loaded=1 failed=0 rendered=1`；手机截图确认主/第二字幕同时分层显示，电视确认独立第二字幕显示且 MPV 已加载并选中主字幕轨。
- 两端五场景预载 E2E 全部 PASS（forward/reverse/retained/cancelled/failure）。
- 本地服务器实测：普通及 suffix Range 返回 206，多 Range 返回 416；目录穿越、根目录删除、文件名穿越和 Zip Slip 均被拒绝，正常上传成功。
- 本地 30 秒 MP4：手机 EXO 与电视 MPV 均发起实际 GET；电视确认 mediacodec 硬解和 `playback restart complete`；生命周期前后台压力测试未见 fatal error。
