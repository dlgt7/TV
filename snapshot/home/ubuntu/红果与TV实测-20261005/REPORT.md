# 红果短剧源与 TV 播放验证报告

日期：2026-10-05。**修复源、三种播放模式、预加载、普通话 ASR 字幕及暂停弹幕开关已通过实测；最终 preview 已安装，原 11 个设置／数据库文件全部保持一致。仍有两项实际失败：长查询上游 500、生产模型下载错误。**

交付文件：[红果短剧加了个排行榜_修复版.py](../红果短剧加了个排行榜_修复版.py)。原文件未修改；最终在线证据对应修复版 SHA-256 `72c2a119e65453335f9ed9f118c3fd23139ff73d5a102ad51f6d42b5e3bd2fcf`。

## 主要结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 源功能 | 99 项记录：96 PASS、1 不适用、1 观察、1 长查询失败。首页推荐、28 类前两页、四榜入口、搜索与详情已执行；源未提供筛选组。 | [最终源功能](final-online-functional.json) |
| 分集解析 | 三部作品共 77、61、34 集；首／中／末集各一，加第一部第 2 集，共 10 个用例。 | [最终源功能](final-online-functional.json) |
| Exo HARD | 10/10 PASS，包括此前失败的六个中、末集后退跳转。 | [HARD](final-online-playback-exo-hard.json) |
| Exo SOFT | 10/10 PASS，实际平台软件解码路径及前后跳转通过。 | [SOFT](final-online-playback-exo-soft.json) |
| MPV | 10/10 PASS，画面、音轨、前后跳转与相邻集切换均有记录。 | [MPV](final-online-playback-mpv.json) |
| 预加载 | 完成预加载、接续使用同一 prepared source、关闭后清理均为 true。 | [预加载](final-online-playback-preload.json) |
| 解码后音频 | 首集 44,100 Hz，51,200 个 PCM 样本，47,732 个非零。 | [PCM](final-online-playback-pcm.json) |
| 普通话 ASR 字幕 | 17.116 秒通过；真实 Exo 音频识别 6 段，实际 SubtitleView 显示 1 次／8 字符，翻译关闭。 | [ASR](asr-final-report.json)、[instrumentation](android-asr-direct-instrumentation.txt) |
| 最新 Android 核心回归 | 67431d565 共 17/17 PASS，55.21 秒，含暂停弹幕、复杂字幕、预加载和原生场景。 | [最终核心回归](paused-final-core-instrumentation.txt) |
| 单测、软件选轨与 lint | 267 个单元测试及 7 个软件选轨场景通过；lint 0 error／312 warnings。 | [软件解码检查](tv-software-checks.json) |
| 真实 UI 主链路 | 导入源→片库四榜→全部热播榜→209 集作品详情→全屏播放→弹幕开关，全部 PASS。 | [UI 步骤](ui-validation.json) |

最终在线一轮约 226.344 秒，三种播放模式均包含实际视频和前后跳转后的新帧检查。十个真实流没有可选字幕轨，原生双字幕／ASS 切换由复杂字幕核心测试另行覆盖。一个播放样本验证了音效开关及暂停／位置保持，不代表全部音效档位的听感评测。

## 两项实际失败

| 项目 | 已确认的结果 | 范围与限制 |
| --- | --- | --- |
| 长查询搜索 | 34 字符查询两次收到上游 500；源返回受控 JSON 空列表，此步骤仍记 FAIL。 | 普通关键词、空查询及短随机查询另测。短随机词返回 6 条建议属于观察，不是精确搜索成功。 |
| 生产模型下载 | AsrModelManager.download 在 120.222 秒后返回 MODEL_DOWNLOAD_ERROR；0 进度、0 字节，onError=true、onComplete=false。 | timedOut=false；RAM 8192 MiB／存储检查通过。未捕获具体 HTTP 状态，不能指定底层原因。备用传输不改变此失败。 |

证据：[源功能](final-online-functional.json)、[下载最终报告](asr-download-progress.json)、[下载 instrumentation](android-model-download-instrumentation.txt)。下载报告虽名为 progress，内容是完整最终结果；未取得文件不等于已确认 SHA 不匹配。

## 已完成的修复

1. 支持上游 deferred JSON，修复原版详情被吞错后返回空列表的问题。
2. 按单页建议接口停止重复搜索页，保留真实分类总数／末页信息，移除无独立上游档位的伪五档清晰度。
3. 修复私有 DEX 校验、应用内启动与复用；原始 Python 文件及嵌入 DEX 均保持不变。
4. 修复 TV 的 SOFT 模式只允许未打包 FFmpeg 渲染器而不选轨的问题。
5. 修复开放尾 HTTP Range 返回偏移内容却声明 200 的问题，恢复正确 206 和范围元数据。
6. 修复暂停弹幕首次加载与隐藏后恢复：保留原始时间、偏移和寿命，排除过期／未来项，处理异步加载，播放状态保持不变。

Range 对照使用同一第 31 集：完整文件 7,177,921 字节，修复前后 SHA 相同。从 1,048,576 字节请求至末尾，修复前为 200 且缺少 Content-Range；修复后为 206，范围 1,048,576–7,177,920／7,177,921，抽查字节匹配。两轮本地完整文件跳转均通过；最终在线 HARD／SOFT 十例另行通过。

证据：[Range 修复前](range-before-report.json)、[修复后](range-after-report.json)、[76 项合成契约](fixed-range-adapter-report.json)、[原件与 DEX 完整性](fixed-range-integrity-report.json)、[详情回归](fixed-regression-report.json)、[分页边界](fixed-pagination-report.json)、[服务就绪](android-startup-diagnostic.json)。

## 暂停弹幕：修复前失败，修复后通过

在旧版 f220d466c，新加的“暂停首次加载”和“隐藏后重新显示”两项回归均真实失败，耗时 18.912 秒；67431d565 修复后，两项随 17 项核心回归全部通过。见[修复前](paused-danmaku-before-instrumentation.txt)、[修复后](paused-final-core-instrumentation.txt)。入口、四阶段偏好变化及重启持久化也已[验证](danmaku-ui-validation.json)。

真实 UI 三张截图均停在 **00:31**：滚动 013、顶部 014、底部 015 三条测试弹幕先可见，关闭后消失，再开启后三条全部恢复；按钮与焦点无裁切。人工像素复核 PASS，见[复核摘要](danmaku-ui-fixed-pixel-evidence.json)及[开启](ui-danmaku-fixed-visible.png)／[关闭](ui-danmaku-fixed-hidden.png)／[再次开启](ui-danmaku-fixed-visible-again.png)。该检查使用本地 XML 测试弹幕，未修改交付源，也不代表真实弹幕接口覆盖。

补丁仅涉及 Media3 的 DanmakuController／DanmakuView；固定基准 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`，经 SHA 和 blob 校验后由本地／CI 同一入口应用。见[补丁说明](https://github.com/wobuhui666/TV/blob/67431d56596a31d179a54feddc14a3a3bb9f61ec/third_party/media3/README.md)。

## ASR 与模型

本地普通话 Zipformer CTC 关闭翻译后无需用户 API key。生产下载失败后，借助 Tailscale HTTP 备用传输继续识别验证；两份公开模型约 154.8 MiB，主机和 sourceprobe 的固定 SHA-256 均通过[校验](model-private-verification.txt)。模型也已加入用户 preview 原先为空的模型目录，没有覆盖已有文件或改动 AI 设置，**预览版无需重新下载模型**，见[安装摘要](preview-model-install.json)。

最终 ASR 报告内耗时 17.005 秒，instrumentation 17.116 秒；源刷新 6.189 秒、观察 10.191 秒。真实生产 PCM 提供 827 个分块，识别 6 段；字幕显示 1 次／8 字符／14,350 个绘制像素。内嵌文本轨已禁用，没有注入伪 PCM、识别文本或字幕。

**droppedPcmChunks=257、droppedSpeechSegments=0**；短样本不能证明长期实时性或稳定性。settingsRestored=true、cleanupErrors 为空，只记录字符数和计数，不保存识别原文。首次 ADB reverse 源刷新失败时识别尚未开始，改为直连后通过；[首次失败记录](asr-first-report.json)保留。

## 安装与数据保全

| 对象 | 最终结果 |
| --- | --- |
| 原始 Python | SHA-256 `21f77a8ebce2f348900c721119f70a0e236d5d897386cf4c9b5cd05911739814`，未修改。 |
| sourceprobe／preview | 验证时均为 67431d565；最终保留 preview，临时 sourceprobe 已卸载。 |
| preview 原有数据 | 最终 11/11 个设置／数据库文件与任务最初 SHA 一致，0 变化、0 缺失。备份保持私有。 |
| 普通话模型 | 新增模型仍保留，设备校验通过；未改动原有 AI 设置。 |

证据：[sourceprobe 安装](paused-sourceprobe-install.txt)、[preview 安装](paused-preview-install.txt)、[最终 APK 完整性](paused-final-verification.json)、[测试 APK 校验](paused-test-verification.json)、[升级前对照](preview-state-compare.json)、[最终 11/11 对照](preview-state-final-compare.json)。源与播放测试在独立 sourceprobe UID 下进行。

最终修复包来自 [67431d565 验证 CI](https://github.com/wobuhui666/TV/actions/runs/37323072749)。同提交的[发布 CI](https://github.com/wobuhui666/TV/actions/runs/37323075194)及 [ARM64／ARMv7 release](https://github.com/wobuhui666/TV/releases/tag/build-37323075194)也已成功。外部 Python 源独立交付。

## 验证边界与收尾

- 代表性分集通过不等于所有作品／每一集、长期连续播放或全网兼容性通过。翻译服务、其他语种和识别准确率未测。
- ARM64 Android 节点结果不等同于实体三星设备、HDMI 音频直通、HDR、DRM 或全部硬件解码能力；[codec 清单](final-online-playback-codecs.json)仅记录该节点能力。
- 历史 instrumentation 的“Process crashed”对应主动 am force-stop，不能据此认定产品自行崩溃；旧失败保留，最终结果以相应新证据为准。
- 独立测试应用、任务设备临时文件及端口映射已清除，三个主机临时服务端口已关闭，preview 已打开首页；原应用包保持不变，preview 保留最终修复版及普通话模型，见[收尾记录](cleanup-report.json)。原 Codespace 已关闭，账单 HTTP 402 阻止重启，非生产临时测试签名文件清理仍待恢复访问后处理。

证据目录不包含媒体源地址、请求认证信息、播放凭据、用户配置内容、私有备份或模型二进制；文件完整性见 [SHA256SUMS.txt](SHA256SUMS.txt)。
