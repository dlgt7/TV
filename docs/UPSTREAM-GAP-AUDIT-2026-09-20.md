# 上游功能缺口审计（2026-09-20）

## 结论

当前 fork 并不是简单落后于上游：上游运行时依赖一个未提交到 Git 的 `app/libs/lib-*.aar`（由 `app/libs/.gitignore` 明确忽略），其中包含定制的 Media3、MPV 和磁盘预载 API。本 fork 的 `media3compat` 是公开源码兼容层。因此，直接复制上游 Java 代码会遇到私有 API 缺失，必须逐项用公开 API 重写。

本轮优先补齐了会造成“设置存在但功能无效”或明显行为错误的项目。大型音视频效果系统建议单独迁移，不应混入修复提交。

## 本轮已补齐（高优先级）

1. **真实 EXO 音量增益**
   - 旧实现把 `1.5f` 传给 Media3 `setVolume`，但 Media3 会钳制到 `0..1`，实际无增益。
   - 现使用 Android `LoudnessEnhancer` 绑定 EXO audio session；MPV 继续使用原生 volume。
   - audio session 变化时重建 effect，关闭或释放播放器时释放资源。

2. **下一集磁盘预载**
   - 原 `DiskPreloadManager.start()` 是空实现，UI 虽有预载设置但不会下载。
   - 现用 Media3 1.10.1 的公开 `DefaultPreloadManager` 与 `specifiedRangeCached` 实现磁盘预载，并复用播放器 `SimpleCache`。
   - VOD 控制器在当前集 READY 后预载正序/倒序方向的下一集；播放时由普通 `CacheDataSource` 消费缓存，不依赖私有 MediaSource hand-off API。
   - 预载失败不影响正常播放，切换源或重置时会清理任务。

3. **外部字幕字体**
   - 旧实现用文件名作为 MPV `sub-font`，文件名和字体内部 family 不一致时会失效。
   - 新增有边界检查的 TTF/OTF/TTC `name` 表解析，导入时校验真实字体并限制 32 MiB。
   - MPV 使用内部 family；播放字幕弹窗已接入系统字体选择器，不再是空回调。

4. **已确认的行为修复**
   - Leanback 列表仅在到底时加载下一页。
   - VOD 倒序播放时按正确方向预载。
   - seek 到结尾时由调用方获知已到末尾。
   - CI Android SDK、原生构建 stdout/stderr、x86_64 FFmpeg JNI 构建目录问题已同步修复。

## 仍缺失但建议后续补齐（中优先级）

### 1. 预载生命周期和可观测性

当前公开 API 方案完成的是共享磁盘缓存，不复用私有 AAR 中预构建的 `MediaSource`/sample queue。建议后续增加：

- 预载完成、取消、错误指标及调试日志；
- 网络类型、按流量计费和低存储空间策略；
- 自动化测试：本地 HTTP 服务提供两集 HLS/MP4，验证正序、倒序、切集命中和清理；
- 评估预载 manager 释放时清除缓存跨度的行为，必要时改为独立 `CacheWriter` 以精确控制保留策略。

### 2. 字体集合与字体元数据

当前解析器可从 TTC 中取得可用 family，但只返回第一个字体。若用户需要 TTC 子字体选择、localized family 或 variable font 实例，需要更完整的 OpenType 元数据模型。普通 TTF/OTF 使用已满足。

### 3. 播放错误恢复与 session 拆分

上游有 `ExoPlayerSession`、`ExoSubtitleController` 和更完整的预载交接逻辑。当前 fork 的播放器管理器体积较大，但现有功能可用。建议以可测试的小提交拆分，不建议整体覆盖。

## 不建议立即照搬（低优先级/大改造）

上游新增了完整效果系统：

- 多段 EQ、声道模式、平衡、中心声道、人声增强；
- 响度归一化、动态稳定、limiter；
- EXO PCM `AudioProcessor` 和 MPV filter 双实现；
- 色调、细节、着色器视频效果与 preset/profile UI。

这些代码规模大、CPU/耗电/延迟风险高，并依赖私有 Media3/MPV AAR 中的扩展（例如 MPV audio mix）。除非用户明确需要 EQ、响度归一化或画质调节，否则不应作为基础兼容修复移植。若移植，应先做基准测试并分为“音频效果”和“视频效果”两个独立里程碑。

## 验证记录

- Mobile/Leanback arm64 release 完整构建成功，包含 javac、lintVital、R8、签名和打包。
- 两台设备安装成功并可启动，无 crash/ANR/`VerifyError`/`NoSuchMethodError`。
- 手机 EXO 直播播放时，`dumpsys media.audio_flinger` 显示当前 app audio session 上存在且启用 `Loudness Enhancer` effect，证明 1.5x 增益不再被 Media3 钳制掉。
- `FontFamilyParser` 对 `DejaVuSans.ttf` 返回 `DejaVu Sans`；手机系统文件选择器成功导入字体。
- MPV 直播和统一暂停层此前已在 TV 设备验证。
- VOD 端到端预载仍受当前视频源请求超时影响，已完成编译、静态链路和失败回退验证；仍需用可控双集测试源补充命中测试。
