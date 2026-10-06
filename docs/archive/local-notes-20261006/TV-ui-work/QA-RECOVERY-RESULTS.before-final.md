# 云端UI验收结果：需修复后继续（2026-10-02 UTC）

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

本轮完成了主要遥控和边界验收，发现并验证了两项仍阻断完成的问题。**未生成 `qa-recovery.complete.json`，不能据此声明改版最终验收通过。** 先前“主题通过”仅指独立冷启路径，组合旅程已发现可稳定复现的崩溃。

## 必须修复

### 1. 无配置首页标题与顶栏重叠

- `recovery-no-config-clean.xml`：标题“下一部好片，从这里开始。”为 `[96,56][1824,130]`，导航为 `[200,32][1736,128]`；截图也明确重叠。
- “选择内容源”可聚焦并进入设置，功能入口通过，布局不通过。
- 只读定位：`HomeActivity` 的无Hero分支虽然设置80dp顶部padding，但Leanback `windowAlignmentOffset`固定0，会将EmptyHome根节点对齐到y=0，其内部28dp文字起点成为56px。
- 建议根代理的源码交付者将无Hero时的顶部padding和windowAlignmentOffset同步为80dp，Hero时均为0；统一覆盖初始化、setFeatured和removeFeatured。保留Hero首屏锚定，不改空状态内部padding掩盖问题。
- 修复后测：无配置冷启、下移选择源再Back、有历史但空推荐、刷新空状态/有Hero互转、普通Hero与列表返回导航。

### 2. 后台直播页遇主题更改会崩溃（两次复现）

- 路径：Home → Search(fast) → 结果详情 → Back → Live → Setting → 直播主页弹窗 → 壁纸配置弹窗 → 应用/主题色彩 → B站粉。
- 主题偏好写入成功，但进入真正的 `CrashActivity`；独立冷启七色遍历不能覆盖此路径。
- 完整错误详情：`theme-repro-error-details.txt/xml/png`。录像：`theme-sequence-repro.mp4`。日志：`theme-repro-logcat.txt`、`theme-sequence-logcat.txt`。
- 栈：`Unable to start LiveActivity` → `PlaybackActivity.player():96` 对 null PlaybackService 调用 `player()` → `LiveActivity.stopPlayer:915` → `stopPlaybackForRefresh:961` → `LivePlaybackController.refresh/selectChannel` → `setPosition:384/setGroup:350` → LiveData观察者307，在ON_START回放频道时服务尚未异步连接。
- 只读复核：LiveActivity、VideoActivity均未覆盖BaseActivity默认的主题 `recreate()`。建议播放页原位更新主题并保留PlayerView、服务和播放状态；不要调用重新初始化播放器的函数。若同时修初始化竞态，需缓存最新Live，服务连接后完整重放，不能简单判空丢事件或只在stopPlayer判空。
- 修复后必须重跑原组合路径、多次换色、返回直播频道保持、点播进度保持；如改连接gate还应验证延迟服务连接最终恢复频道。

## 已完成且有证据的项目

| 项目 | 证据与准确范围 |
|---|---|
| 真实遥控选集 | 右→下→下进入episode控件，选第2集，全屏标题确认为第2集；`recovery-episode*`。旧episode-two图实为收藏，已作废其选集结论。 |
| 继续观看进度 | 保存第2集51241ms/120095ms，从首页实际继续观看卡进入后控制栏57秒，差值符合进入期间播放；`recovery-before-card-history.json`、`recovery-real-continue-focus.*`、`recovery-resume-card.mp4`。 |
| 换源 | 仅给fixture增加备用片库；按“换源”后详情站源从演示片库变备用片库；`recovery-source-changed.xml`、`recovery-source-player.mp4`。 |
| 播放层级 | 设置抽屉→控制栏→中心信息层→全屏→详情小窗；`recovery-player-*`、`final-player-controls.*`。 |
| 直播 | 003城市→纪录分类→005自然纪实，下键快捷到006人文故事；覆盖层确认真实选中项，Back关闭；`recovery-live-navigation.mp4`。 |
| 搜索 | fixture旧slow延迟25秒，编辑并用屏幕键盘搜索键提交fast；fast先到，旧响应后未覆盖且焦点保持，详情Back回结果卡；`recovery-search-replace.mp4`。后续静态重拍加入第二fixture源，所以截图显示2条，原延迟用例录像为1条。 |
| 图片与标题 | 横图、竖图、404图片字母占位、长中文名均可显示并聚焦；`final-missing-art-card.*`、`final-long-title-card.*`、`detail-long-title.*`。 |
| 空历史/收藏 | 首页不占空历史分区；完整历史有“还没有观看记录”，收藏通用空状态；`recovery-empty-history*`、`recovery-empty-favorites.*`。临时测试DB已恢复。 |
| 我的/推送/文件/发现 | 页面可见；文件权限授予后可浏览目录。CastActivity可启动/返回，仅空闲界面，无真实发送端投屏。 |
| 设置焦点修复 | 修复后七种色彩都能纯D-pad到达（含横向滚动隐藏两色），每次读取偏好值核对；粉色冷启持久化、上下/左右边界通过。组合旅程崩溃单列为阻断。 |
| 设置主子动作 | VOD/LIVE/WALL主点击和长按配置、主页子按钮、壁纸默认/刷新均可达。配置历史无其他条目时弹窗自动关闭；不是有历史数据回归。MPV/JS开关可操作，导出子按钮明确提示暂无日志；有内容文件导出未测。 |
| 快连按/后台 | 144次横向连按后可回导航；后台5秒后仍原hero内容与焦点；`recovery-fast-keys*`、`recovery-background-*`。 |

## 构建与源码对应

- 本代理未编写生产源码；按根代理授权仅同步交付者的 `JetStreamSettingView.kt`，SHA `dfdc063d7532e95d8f57e943161397806ff1e67587a0f089cc78ad66bfb4a514`。
- 同步前校验云端旧hash，未覆盖其他生产文件。云端增量 `assembleLeanbackArm64_v8aDebug` + `testLeanbackArm64_v8aDebugUnitTest` + `lintLeanbackArm64_v8aDebug` 成功，2分22秒。
- `final-reports/final-verification.json` 已刷新，105个源码SHA与当前已构建树一致；216测试，0失败/错误/跳过；lint269 warning、6 hint、无error。
- 生产ARM64 Debug APK SHA：`e6275e1817027023d0559fd5e57fa684da15ee24bab93af99ef572c6d1518e84`。
- 独立x86 UI APK SHA：`b5d8bafff2e476640b5e48237250ae6465fdd3b0ef7291965bdd916e4309b3a5`。
- 上述产物**尚未包含两个新阻断的修复**；修复后必须重新同步具体改动、增量验证、重生成测试APK并刷新报告。

## 性能及日志限制

- API24 x86_64软件渲染模拟器短测PSS84253→84855KiB，增加602KiB；只可说明该短程未见明显无界增长，不能证明无泄漏。
- 500帧中496帧janky（99.2%），p50=53ms，p95=85ms；不能声称60fps或性能验收通过，须真机评估。
- 一次快连按后导航宿主尺寸出现density2→1.125比例变化；干净冷启后普通列表返回未复现。保留观察项，未强行归因于Compose。
- 原完整日志的系统FATAL来自API24 `uiautomator.AccessibilityNodeInfoDumper`自身NPE，非app崩溃。其后新发现的主题组合路径则是**真正app崩溃**，不能混为一谈。
- 无配置时VodConfig/LiveConfig缺URL有捕获并打印的NPE，UI继续显示空状态；不应写“日志无异常”。显式am force-stop对应的活动强制结束不算自发崩溃。
- 本模拟器不能验证当贝H3S/坚果J10S、生产ARM播放、Python/MPV、硬解、真实投屏/网络兼容、真实EPG及其他设备API。测试APK替换x86库并绕过PyLoader eager初始化，生产APK无此绕过。

## 证据、脚本与云端状态

- 本地完整证据：`/home/ubuntu/TV-ui-work/recovery-evidence/final-qa/`；归档：`recovery-final-evidence.tar.gz`。
- 云端证据：`/workspaces/TV-ui-results/final-qa/`。18份录像已逐份ffprobe并全帧解码（结果见video-validation.json）。
- 旧录屏137.362511秒、1280×720 H264、1293帧已确认可解码；其他旧录像曾过早复制缺moov，已从设备最终文件重新取回并验证。
- QA停止录屏已改为API24支持的 `pkill -l 2 screenrecord`，进程已结束可幂等处理；XML取证有限重试并30秒超时。已改为先dump再截图，减少转场前截图与转场后XML不一致。
- 旧误导文件名及转场前图不可直接当通过证据；`fixed-theme-2.*`目前保存第一次组合主题崩溃，不能当正常主题图。联系表 `contact-final-*`为发现问题时的审阅快照，最终修复后需重新生成供PR使用。
- 当前模拟器保留第二次崩溃的“错误信息”详情页，方便交付者定位。没有QA脚本/录屏继续运行（归档完成后），Codespace与模拟器未关闭。
- fixture保留120秒测试视频（原8秒另存sample-short.mp4）、slow/fast延迟用例和备用源；原服务器备份 `/tmp/tv-ui-fixture/server-before-recovery.py`，临时DB备份 `/tmp/tv-ui-recovery-state/original.sqlite`。
- 未提交、未推送、未创建PR、未发送外部通知。等待根代理/交付者修源码后可从既有模拟器继续，不需重做无关验证。
