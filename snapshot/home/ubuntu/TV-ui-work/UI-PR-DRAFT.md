电视端改为中性深灰、白色焦点及内容优先排版，覆盖首页、片库、发现、搜索、详情、播放、直播、我的与设置。首页展示真实源主视觉和继续观看，详情保留小窗自动播放，选集与全屏操作更直接。保留播放内核、View/Leanback/Compose 架构及 API 24 支持，基于已合并的 #61。

修复首次与异步刷新焦点、图片越界及长标题排版；设置主操作和子按钮可独立用遥控导航；无配置首页标题避开顶栏；主题变化原位更新播放界面，保留 PlayerView、服务连接和进度。

验证：最终105项源码 SHA-256 全匹配；云端以下检查通过：

```sh
./gradlew :app:assembleLeanbackArm64_v8aDebug :app:testLeanbackArm64_v8aDebugUnitTest :app:lintLeanbackArm64_v8aDebug --offline --no-daemon --build-cache --max-workers=2
```

216项单测全部通过；lint 0错误、269警告、6提示。共享代码 mobile Java 编译于10月1日通过（`:app:compileMobileArm64_v8aDebugJavaWithJavac`，之后电视端修复未重跑 mobile）；API24 ART专项31项通过。最后两项阻断已回归，无配置/空推荐布局、完整搜索→详情→直播→设置→多次换色、频道/19秒暂停进度/详情焦点保持均有证据；Live/Video 的 Activity 和 PlayerView 对象标识切色前后相同。

[实现说明与截图对照](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/README.md) · [完整QA及限制](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/qa-results.md) · [验证哈希](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/verification.json) · [28段录像索引](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/evidence/README.md)

| 首页改版前 | 首页改版后 |
| --- | --- |
| ![before](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/evidence/home-before.png?raw=true) | ![after](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/evidence/home-after.png?raw=true) |

精选录像：[主题完整旅程](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/evidence/verified-theme-complete-journey.mp4)、[点播保持](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/evidence/verified-vod-theme-preservation.mp4)、[无配置首页](https://github.com/wobuhui666/TV/blob/ui/apple-tv-redesign/docs/ui-redesign/evidence/blocker-no-config.mp4)。

限制：运行验收使用API24 x86_64软件模拟器与合成片库，独立测试APK替换x86库并跳过测试DEX的 eager PyLoader；生产ARM APK运行、当贝H3S/坚果J10S、Python/MPV、硬解、真实投屏及真实EPG未实测。软件模拟器500帧中496帧janky（99.2%，p95 85ms），PSS短测84253→84855KiB，不能声明性能或无泄漏验收通过。主题触发重建已消除，系统自身重建的服务连接竞态未全面修复/验证；空配置捕获异常日志仍存在。
