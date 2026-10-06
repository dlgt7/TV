已完成 TV 仓库首轮优化检查。最值得先处理的是图片失败恢复、媒体库搜索等待、首页磁盘访问，以及携带 AI 凭据的 HTTPS 请求校验。以下区分了实际复现、源码确认和待真机测量的建议。

检查日期：2026-10-01。仓库：`wobuhui666/TV`；分支：`sync/fongmi-20260628`；提交：`577cb0921516e3b85173aec354d4c2893d7ab33c`。

本地源码位于 `/home/ubuntu/TV`。构建、测试与数据库实验均在 `tv-build-5g6j76qj97vg37wpr`（4 核、16GB）执行，独立检出位于 `/workspaces/TV-review-20261001`。原 `/workspaces/TV` 中已有的 Compose BOM 和 TV Material 依赖改动未纳入基线，也未覆盖。测试使用 JDK 21、现有 Android SDK 和 Media3 源码 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`，Media3 保留现有的裁剪模块设置。

| 建议顺序 | 问题 | 证据与影响 | 建议 |
| --- | --- | --- | --- |
| 优先 | AI 请求关闭证书和主机名校验 | 源码确认；请求携带 Bearer 凭据 | AI 客户端采用系统证书与主机名校验 |
| 优先 | 图片失败后永久跳过重试 | 源码确认；临时断网也会被永久记入失败集合 | 给失败记录设置过期时间和容量上限，支持恢复重试 |
| 优先 | 媒体库搜索逐个等待超时，完成后不取消剩余任务 | 实际复现；已有 50 条可用结果仍等待 10,003ms | 按完成顺序收集，采用统一截止时间，取消剩余任务 |
| 其次 | 首页历史查询在主线程全表扫描和排序 | 调用链与 SQLite 实验确认 | 联合索引及异步查询，加入 Room 迁移 |
| 其次 | 首页海报重复下载、同步清理缓存，缺图回退仍保持模糊 | 源码确认；实际流量和掉帧尚未测量 | 共用图片缓存、按尺寸取图、异步增量维护缓存、区分模糊状态 |
| 其次 | 详情页重复查询相同影视元数据，Logo 请求无法取消 | 源码确认 | 合并搜索结果，增加有界缓存与页面级取消 |
| 后续 | 发布版未启用代码与资源压缩 | 配置确认；尚未做 APK 体积对照 | 先补齐反射/JNI 保留规则与播放回归，再评估开启 |

1. **AI 请求应使用校验证书的独立客户端。**

   [OkHttp.java](/home/ubuntu/TV/catvod/src/main/java/com/github/catvod/net/OkHttp.java:190) 把主机名验证器设为恒真，`checkServerTrusted` 也是空实现；`client(timeout)` 从这个客户端派生。[AiSkipApi.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/ai/skip/AiSkipApi.java:102) 和 [OpenAiSubtitleTranslator.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/ai/subtitle/OpenAiSubtitleTranslator.java:89) 都使用它发送带 Bearer 凭据的请求。在网络被中间人控制的条件下，HTTPS 无法验证服务端身份。

   建议优先让 AI API 使用默认信任管理器和主机名校验；如果个别影视源确实需要兼容自签名证书，应将例外限制到明确配置的源。验证应覆盖不可信证书、主机名不匹配和正常证书三种情况。这是代码中的具体安全问题，不代表本次检查发生过凭据泄露。

2. **失败图片缓存缺少恢复机制，并且占用会持续增长。**

   [ImgUtil.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/utils/ImgUtil.java:43) 使用进程级 `HashSet` 保存失败 URL；第 151 行把任何加载失败加入集合，第 107 行以后每次遇到这个 URL 都直接显示占位图。没有 TTL、容量上限、成功移除或网络恢复清理。临时超时之后，即便恢复网络，同一进程内再次访问该图片仍不会发起重试。

   同一分支直接 `setImageDrawable`，没有取消该 ImageView 先前的 Glide 请求；列表复用时还需防止旧图片完成后覆盖新的占位状态。建议用有界、短期的失败缓存，区分临时网络错误，并在直接设置占位图前取消旧请求。验证用“失败 → 网络恢复 → 同 URL 重试成功”及列表快速复用场景。

3. **媒体库搜索存在可复现的等待与任务清理问题。**

   [VodBrowse.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/browse/VodBrowse.java:94) 对每个 Future 顺序调用 `get(5, SECONDS)`，超时后继续等下一个；达到 50 条结果后直接退出，未取消未完成任务。[PlaybackService.onSearch](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java:684) 在公共任务池中执行这段收集工作。

   在原代码方法上注入两个未完成 Future，后面放入已经完成的 50 条结果，耗时 **10,003ms**；另一个实验先提供 50 条结果，返回后剩余 Future 的 `isCancelled()` 为 **false**。探针通过反射调用实际 `collectResults`，没有复制或替换其算法。

   建议采用完成队列和统一截止时间，并在成功、超时、取消路径统一取消剩余 Future；网络层也应支持取消正在执行的请求。此结论针对 MediaLibrarySession 的媒体库搜索路径；普通 TV 搜索使用不同的 `ViewModelSearchRunner`，不能直接把这个 10 秒结果套用到普通搜索页面。

4. **首页历史查询可通过索引和异步化降低开销。**

   [HomeActivity.java](/home/ubuntu/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java:518) 在主线程刷新事件中调用 `History.get()`。[HistoryDao.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/db/dao/HistoryDao.java:16) 按 `cid`、`createTime` 过滤并排序；当前 Room v36 schema 只有主键，没有覆盖这条查询的联合索引。

   使用仓库实际建表语句，在 Codespace 的内存 SQLite 中生成记录并重复查询 25 次：

   | 模拟记录数 | 原查询中位数 | 加 `(cid, createTime)` 索引后 | 查询结果 |
   | --- | --- | --- | --- |
   | 5,000 | 0.7827ms | 0.1710ms | 相同的 60 条记录 |
   | 50,000 | 8.3835ms | 0.1570ms | 相同的 60 条记录 |

   执行计划由 `SCAN History` 加临时排序树，变为按联合索引查找。数据是合成数据，时间包含 Python 取回结果的开销，不能作为电视实际性能或整体提速比例。建议新增索引时同时升级 Room schema、添加保留用户历史数据的迁移，再把首页查询转移到后台。`findByName` 的 `(cid, vodName, createTime)` 索引应根据实际调用量另行评估。

5. **首页海报链路有重复工作，也有回退显示错误。**

   [FeaturedVodPresenter.java](/home/ubuntu/TV/app/src/leanback/java/com/fongmi/android/tv/ui/presenter/FeaturedVodPresenter.java:219) 找到 TMDB 海报后，一边交给 Glide 显示，一边通过 `FeaturedPosterCache.put` 再用 OkHttp 下载同一图片。[TmdbLogoHelper.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/utils/TmdbLogoHelper.java:27) 默认请求 `original`。两个下载路径未共用 Glide 的磁盘缓存，慢网下会增加传输量。

   [FeaturedPosterCache.prepare](/home/ubuntu/TV/app/src/leanback/java/com/fongmi/android/tv/utils/FeaturedPosterCache.java:43) 在 Presenter 绑定时读取缓存元数据；列表签名一旦变化，整目录删除并写回元数据，仍保留的海报也会失效。建议按照 URL/媒体标识逐项缓存，用显示尺寸决定图片档位，磁盘读写放到后台；下载应合并进行中的相同请求。

   另一个确定的回退错误在 [transitionToArtwork](/home/ubuntu/TV/app/src/leanback/java/com/fongmi/android/tv/ui/presenter/FeaturedVodPresenter.java:263)：首次加载把源海报模糊显示并把 URL 写入 tag；TMDB 无结果或失败时，回退用同一个 URL，马上被“URL 与 tag 相同”判断提前返回，因此本轮仍显示模糊图。应把 URL 与变换状态一起作为标识，或显式重新加载未模糊的源图。界面现象尚未做真机验证。

6. **详情页可以复用元数据，减少重复查询及离开页面后的请求。**

   [VideoActivity.renderDetail](/home/ubuntu/TV/app/src/leanback/java/com/fongmi/android/tv/ui/activity/VideoActivity.java:717) 同时获取 Logo 和评分。Logo helper 与 rating helper 分别用相同的标题、年份、类型搜索 TMDB；前者还会继续请求 images。它们没有共享查询结果。Logo helper 不返回取消句柄，也没有页面级请求 tag；回调中的有效性判断仅阻止显示旧结果，无法停止下载。

   建议一次搜索得到媒体 ID、评分和图片信息，增加按 endpoint/媒体标识区分的有界 TTL 缓存，复用进行中的请求，并在页面退出时取消。保留已有世代校验以处理请求已经完成的竞态。实际减少多少流量需要请求计数对照。

7. **压缩发布包应在上述问题之后评估。**

   [app/build.gradle](/home/ubuntu/TV/app/build.gradle:84) 当前同时关闭 `minifyEnabled` 和 `shrinkResources`。项目包含 Media3、MPV、Python、动态 Spider/JAR 和 JNI，不能只把开关打开就认定可用。建议先在候选构建中测试保留规则，对照 APK 的 DEX、资源、原生库占比，再验证各播放引擎、外部源、字幕和旧设备；本轮没有给出未经测量的包体缩减百分比。

测试结果与边界：

- Android 基线命令：`./gradlew :app:testLeanbackArm64_v8aDebugUnitTest --offline --no-daemon --build-cache --max-workers=2 --console=plain`。共 **166 项，165 通过，1 失败**；构建约 2 分 6 秒。失败为 `MediaMatcherTest.keepsChineseLettersDuringNormalization`：期望 `庆余年12`，实际 `庆余年12集`。应明确集数标记的规范化规则，再修实现或测试；不应直接删掉断言。[实现位置](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/source/MediaMatcher.java:90)。
- 额外审查探针与 MediaMatcher 测试共 10 项，4 项失败：包括上述原有失败，以及本轮复现的两个搜索问题和一个匹配问题。探针失败表示缺陷成功复现，不表示修复完成。
- 匹配探针显示 `Example Season 1 (2024)` 与 `Example Season 2 (2024)` 被 `compatibleWith` 判为兼容。[MediaIdentity.java](/home/ubuntu/TV/app/src/main/java/com/fongmi/android/tv/source/MediaIdentity.java:46) 在年份相同时提前返回，跳过季数判断。不过本轮静态搜索发现生产源码中只有 `sameMedia` 包装这一调用，未找到实际 UI 调用者；应视为已复现的函数缺陷，不宣称当前聚合列表已经因此混季。聚合键本身仍包含季数。
- Worker：`npm ci --no-audit --no-fund` 后，`npm test` **15/15 通过**，`npm run typecheck` 通过；测试使用模拟绑定，没有部署或调用真实 AI 服务。
- Android lint：离线首次执行缺少 `lint-gradle:32.0.1` 缓存；改为联网执行后 **通过，0 errors、291 warnings**，耗时约 3 分 57 秒。警告包括 92 项未使用资源、33 项全量列表刷新及 3 项 16KB 原生库对齐提示；这 3 项均指向同一个 `pine-core.aar` 中的 `arm64-v8a/libpine.so`，应在适配 16KB 内存页设备时升级或重编译并验证。这里的“通过”指仓库当前 lint 配置下没有阻断错误，并非没有待处理问题。[完整 lint 报告](/home/ubuntu/TV-review-20261001/TV-review-results-20261001/lint-results-leanbackArm64_v8aDebug.html)。
- 本轮没有运行真机/模拟器播放、帧率或内存剖析，也没有制作发布 APK。UI 卡顿幅度、播放兼容性和包体收益仍需设备或构建对照验证。

业务源码保持在检查基线。本轮审查探针只临时放入云端独立检出，运行后移除；脚本、探针源码与结果留在报告目录，便于后续按问题逐项修复和验证。

原始证据：[测试汇总](/home/ubuntu/TV-review-20261001/TV-review-results-20261001/test-summary.json)、[数据库实验](/home/ubuntu/TV-review-20261001/TV-review-results-20261001/history-benchmark.json)、[云端结果归档](/home/ubuntu/TV-review-20261001/codespace-results.tar.gz)。
