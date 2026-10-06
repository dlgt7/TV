# UI QA 接续实测（进行中，2026-10-02 UTC）

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../README.md)。

仅操作测试 fixture/API24 x86_64 模拟器与 QA 脚本；未改生产源码、未重构建、未推送。

- 云端 `/workspaces/TV-ui-results/final-qa` 已有旧截图和完整日志。`remote-playback.mp4` 137.362511秒，1280×720 H264，1293帧，3,959,709字节；`ffmpeg -nostdin -v error -xerror -err_detect explode -i ... -vsync 0 -f null -` 全帧解码通过，错误日志为空。原默认null mux时间基的DTS警告不构成坏帧；改用VFR参数验证。
- QA `stop_record` 已改幂等：screenrecord正常结束后pkill返回1不再打断后续验证。
- 原fixture每集8秒会频繁自动跳集；保留sample-short.mp4，测试片段只在fixture扩为120秒以验证进度。
- 遥控从详情小窗右→下→下进入实际episode控件，左到第1集→右→确认，第2集焦点与选择截图/录像 `recovery-episode*`，全屏标题“深空回声：第2集”确认不是误点收藏。
- 第2集暂停，历史DB记录42159ms/120095ms；后一次记录51241ms。从冷启首页下→下实际聚焦包含“第2集、剩余2分钟”的继续观看卡，按确认进入详情再全屏，标题第2集、时间00:57/02:00。差约6秒符合进入页面和取证期间播放。证据 `recovery-before-card-history.json`、`recovery-real-continue-focus.png/xml`、`recovery-card-open-controls.png/xml`、`recovery-resume-card.mp4`。
- 旧 `episode-two.png` 实为收藏，不可据此写选集通过；旧 `favorites-empty.png` 实际有收藏，不可据此写空收藏通过。
- 旧截图联系表已核验：首页、详情、长中文名、缺图、搜索、我的、推送、设置基本布局可见正常；文件页旧图是系统权限对话框，尚不算文件页验收。

后续：直播/设置/搜索返回、无配置/空历史/缺图、后台恢复、快连按、帧/内存观测；刷新00:29源码对应验证报告。硬件、ARM真实播放、Python/MPV/硬解/投屏联动均不可在此测试APK推定通过。

## 阶段补充 01:55 UTC

- `continue-report.sh` 已刷新且取回 `final-reports/final-verification.json`，105个源码SHA逐一比对当前本地树全部一致。结果216单测、0失败/错误/跳过；lint 269 warning+6 hint、无error。生产ARM APK sha256 `93cfa74000ee772bd476ca2de107b6489388b80a2fd91498c29adc06e41d1ad0`；独立UI fixture APK `9259c2869468dccf47554800048879e57b17a0fb5c2113fe8a6ba7e7426376da`。
- 直播覆盖层通过：确认键展开分类/频道，焦点风景频道，下两次+确认选台后覆盖层消失；正补新频道的再次打开验证。
- 文件浏览：授予测试APK存储权限后可见Alarms/DCIM/Download/Movies列表并获得焦点，`recovery-files-granted.*`。CastActivity空闲页面可启动/返回，但无真实投屏发送端，不能算真实投屏通过。
- 设置左右布局与来源/应用分类切换可用。发现主题色彩整行获得焦点后Right仍停父行、OK无动作，疑似嵌套焦点缺陷，正在做子按钮对照。`recovery-theme-pink/blue-restored`仅旧脚本命名，**不能作为主题切换通过证据**；未确认实际色值变化。

## 阶段补充 02:05 UTC

- 主题缺陷已对照确认：`recovery-theme-probe-right-ok`在父行连续Right×6+OK无动作；Tab一次进入“星际蓝”子按钮，Right×2+OK可到“B站粉”。`recovery-theme-focus-defect.mp4`提供完整对照。已交根代理安排最小源码修复；修复前不写主题通过。之后已通过可达的子按钮恢复“星际蓝”。
- 直播第二组取证：城市频道003→纪录分类→自然纪实005，关闭覆盖层后下键快捷到人文故事006；再次打开覆盖层分别确认实际选中频道。`recovery-live-navigation.mp4`与对应XML。
- 空历史：首页隐藏空历史分区，完整历史页显示“还没有观看记录”；空收藏页显示现有通用空状态，无布局异常。为此仅对测试数据库做备份→临时清空→恢复，原数据库已恢复。
- 干净无配置状态（同时清空Config/Site/Live）首页隐藏直播，焦点“选择内容源”，确认后进入设置来源页；`recovery-no-config-clean.*`及`recovery-no-config-settings-entry.*`。先前仅清Config保留Site的混合状态不能代表干净初装，未据其确认入口失败。
- 当前执行：25秒延迟旧搜索slow→编辑fast→提交→等待旧响应回到客户端，验证旧结果隔离；之后做快速键/后台/性能取证。等最小源码修复后必须增量构建、重装并回归设置，完成标志尚未生成。

## 修复后验证 02:23 UTC

- 根代理指定的设置焦点修复 `dfdc063d7532e95d8f57e943161397806ff1e67587a0f089cc78ad66bfb4a514` 已单文件同步（先校验云端旧hash），本代理未编写生产源码。云端增量 assemble+unitTest+lint 2分22秒通过，已重生成/重装独立x86 APK。
- 最新105源码SHA再次全匹配，216单测0失败；lint269warning+6hint无error。新的生产ARM APK SHA：`e6275e1817027023d0559fd5e57fa684da15ee24bab93af99ef572c6d1518e84`；UI fixture APK：`b5d8bafff2e476640b5e48237250ae6465fdd3b0ef7291965bdd916e4309b3a5`。之前的APK hash仅历史。
- 五色纯D-pad遍历、确认与持久化全部通过。每次选择都读取SharedPreferences核对值；粉色冷启重开仍为-39271。下键退出到图片尺寸、上键返回色彩、最左左键回分类通过。默认蓝色已恢复为-1。证据 `fixed-settings-themes.mp4`、`fixed-theme-*.png/xml/*prefs.json`。
- 慢搜索有效用例通过：fixture slow延迟25秒、fast立即返回带fast片名（必须匹配应用真实过滤策略）；屏幕键盘搜索键提交fast后1条结果先到，旧slow返回后仍fast且焦点“全部”；进详情返回后结果卡位置恢复。`recovery-search-replace.mp4`及`recovery-search-{fast-results,late-response-ignored,return-result}.*`。物理Enter不触发屏幕键盘搜索动作的旧脚本不作为证据。
- VOD主操作点击/长按、右侧主页子按钮已在初始化Home来源后验证可用。配置历史fixture无其他历史条目，点击后弹窗会自动关闭，旧串行QA随后Back导致离开设置；该后半段截图不作为有效验证，正在以每项重新定位的独立动作验证LIVE/WALL及其长按。无生产崩溃结论由最终完整日志另行核验。

## 最终画面复核补充（尚未完成）

- 17份录屏现已全部从设备最终文件取回并ffprobe/全帧解码通过；此前部分拷贝在screenrecord写moov之前，已纠正。API24 toybox实际停止命令为`pkill -l 2 screenrecord`，不能用`pkill -2`。无坏录屏作为最终证据。
- 完整日志唯一FATAL来自API24 `com.android.uiautomator.core.AccessibilityNodeInfoDumper`自身的NPE，app仍存活；非app崩溃。无配置时VodConfig/LiveConfig缺URL有捕获并打印的NPE，UI继续显示空状态，不应声称“日志无异常”。强制结束活动日志与QA显式`am force-stop`对应。
- 换源元数据从演示片库→备用片库确认；播放设置抽屉→控制栏→中心信息层→退出全屏→详情小窗焦点返回确认。
- 快连按144次后可回导航，后台返回保持hero内容/焦点。PSS84253→84855KiB（+602KiB）；模拟器500帧496帧janky，p50=53ms、p95=85ms，不能写性能/60fps通过，也不能以此推定两台真实设备性能。
- 一次快连按后nav宿主尺寸呈density2→1.125差异，干净冷启重做普通列表返回未复现。仅记观察项，未强行归因Compose。最终普通冷启/返回nav均[200,32][1680,128]。
- **确定待修：无配置空状态标题与顶栏重叠。** `recovery-no-config-clean.xml`标题[96,56][1824,130]，nav[200,32][1736,128]。padding不能阻止Leanback零keyline把EmptyHome对齐至y0；根代理已收到限定无Hero分支同步windowAlignmentOffset与80dp padding的修复建议。此项修复/回归前不写完成标志。
- 部分旧静态图在转场前截取，XML在转场后，二者时序不一致；已调整QA为先dump等待再截图，正在重拍用于交付的关键图。结果判断以已核对XML、录屏和静态图综合为准，不能仅信文件名。
