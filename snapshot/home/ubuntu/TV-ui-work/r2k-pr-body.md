电视端改为中性深色、细白焦点与内容优先排版，覆盖首页、片库、发现、搜索、详情、播放、直播、我的和设置。保留 API 24、现有 View/Leanback/Compose 架构及播放内核/服务所有权；基于已合并的 #61。

修复详情视频圆角与左右对齐、首页导航中心线、搜索建议覆盖结果和卡片边缘裁切。采用 AndroidX TV Material 统一按钮与 Surface，设置主/辅助操作独立聚焦，七种主题切换不再挤动相邻按钮。文件列表移除双重焦点圈，播放抽屉显示操作名称及当前值。从“全屏观看”进入后返回恢复原按钮；发现内容请求失败后显示重试提示，确认键可重试，旧回调受请求代次检查。

当前交付候选 **R2K（2026-10-03）**，PR 保留草稿；已知画面缺陷已修复并按实际证据复核，**全页面、全状态与真机验收尚未全部关闭**。

新增真实设备修复：三星 SM-F900F 添加源后，GoProxy 将服务器返回的 XML `NoSuchKey` 当作原生库加载，后台线程错误导致宿主退出。现将两处源初始化入口置于专属线程组，仅隔离直接源插件 LinkageError，其他异常保留原处理；取消后不发布初始化结果。R2K 已覆盖安装三星 API33，保留原配置冷启动时同一错误再次触发并被隔离，PID 与首页持续保持，观察到真实搜索和播放页面。[修复与设备证据](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/source-crash/README.md)。源服务器缺失的组件本身未修复，相关功能仍可能不可用。

验证：Codespace ARM64 debug 构建、223 项单测（含 7 项插件线程专项）、Android lint 通过；lint 无错误，311 warning、6 hint。129 个源码文件与构建清单逐一匹配。遥控与截图覆盖详情长标题/360 集、文件焦点、七主题、片库入口、播放器抽屉首末项、全屏返回焦点、直播分类/频道及发现失败空态。

```sh
./gradlew :app:assembleLeanbackArm64_v8aDebug \
  :app:testLeanbackArm64_v8aDebugUnitTest \
  :app:lintLeanbackArm64_v8aDebug \
  :app:compileMobileArm64_v8aDebugJavaWithJavac \
  --no-daemon --build-cache --max-workers=2 --console=plain
```

[本轮修复、截图与完整限制](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/visual-rework/README.md) · [R2H 独立复核](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/visual-rework/R2H-INDEPENDENT-REVIEW.md) · [源码清单](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/source-crash/r2k-source-sha256.json) · [独立文件/主题复核](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/visual-rework/R2F-VISUAL-REVIEW.md)

| 长标题详情 | 播放设置抽屉 |
| --- | --- |
| ![详情](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/visual-rework/evidence/detail-long-r2f.png?raw=true) | ![抽屉](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/visual-rework/evidence/player-drawer-last-r2f.png?raw=true) |

ARM64 debug APK SHA-256：`bdda4c638ffb03288f4218ea9b2e6780a48204a48c67a224c9735bcf24295f64`。APK 不存入 Git。

限制：当前构建没有 TMDB key，发现页在线成功内容及失败→成功重试未实测；多行长搜索/慢响应、全部弹窗/权限分支、真实 EPG 等组合覆盖仍不完整。大部分视觉运行证据来自 API24 x86_64 软件模拟器、合成片库和临时 UI 包装 APK；R2K 另有三星 API33 原始 ARM64 包的源崩溃修复证据，仍不代表 Python/MPV、硬解、真实投屏或真机性能全面通过。旧 QA/录像仅保留历史功能证据，不作为本轮视觉通过证明。
