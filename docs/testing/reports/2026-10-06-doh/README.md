# DoH 崩溃修复与 wex 实测（2026-10-06）

> 记录范围：以下结论对应报告所列日期、提交和设备；后续版本应重新核对。公开证据见本目录；原文提到的 APK、私有文件及临时脚本不随本次归档。来源见 [导入清单](../../../archive/local-notes-20261006/IMPORTS.json)。

已修复设置页切换 DoH 的主线程网络崩溃，提交并推送默认分支，最终完整 CI 预览版已安装。用户 wex 的师兄旧入口已单项迁移；源搜索没有全部恢复，未解决项目列在下方。

## 交付与构建来源

- 崩溃修复：`01b8748c1`；DoH POST 与诊断扩展：`d89e4072c`。
- 默认分支 `ui/apple-tv-redesign` 已到 `53ffe70b1`（含实测说明）；工作树干净。
- 两轮完整验证 CI：[37404706669](https://github.com/wobuhui666/TV/actions/runs/37404706669)、[37409022443](https://github.com/wobuhui666/TV/actions/runs/37409022443)，共享网络单测、mobile 编译、preview/sourceprobe 应用及测试 APK 均通过。
- 最终正式构建 [37412116582](https://github.com/wobuhui666/TV/actions/runs/37412116582) 已成功，已发布 [build-37412116582](https://github.com/wobuhui666/TV/releases/tag/build-37412116582)，含 ARM64 / ARMv7 APK。前一版崩溃修复已发布 [build-37406032802](https://github.com/wobuhui666/TV/releases/tag/build-37406032802)。
- 已安装文件：`TV-DoH与DNS修复-preview-arm64.apk`。
- APK SHA-256：`9ba8fa167fa5b30a4a8120ec130361a5baec05bd002ed4f909db342bba0585ca`。

安装包来自 `d89e4072c` 的完整 CI，只重签名，1833 个非签名 ZIP 项内容逐项一致。因完整文件传输超时，最终用设备旧 APK 的相同压缩区段及 CI 新字节重建；主机及设备重建后哈希都与完整已签名 CI APK 一致，未修改字节码或拼入本地编译应用代码。

最终覆盖安装前后、首次启动前的 11 个设置/数据库文件哈希完全一致，ECH/DoH 偏好保持原值；首页确认 resumed。见 `preview-install-verification.json`。

## DoH 崩溃修复

原设置线程同步调用 `connectionPool.evictAll()`，Conscrypt 关闭 TLS socket 可能写 close_notify，触发 NetworkOnMainThreadException。现在同步失效 ECH 缓存，将空闲连接关闭交给有界后台单线程，合并密集切换；不取消活动请求。缓存代次检查避免旧 DoH 快照回填。

新增三项本地 JUnit 测试通过。完整 CI 的共享网络测试任务实际执行成功。两版完整 APK 均在 Android 13 / ARM64 设备通过 `DohSwitchRegressionTest`：主线程严格网络策略下两次变更通过、两个真实空闲 Conscrypt TLS socket 关闭、一个保持活动的响应继续读完。最终结果见 `doh-switch-regression.json`。

## wex 的实际结果

使用用户真实配置入口、同一搜索词与真实 JarLoader，在独立 sourceprobe 包测试。未发布搜索词、配置正文、凭据或原始异常栈。配置入口审计覆盖 76 项；功能抽测不等于 76 项全部可用性验收。

| 源 | 实测与处理 |
| --- | --- |
| 小龙 | 最终完整 APK：阿里 DoH、ECH 开启，返回 9 条正常结果，实际 Conscrypt 连接、HTTP 200。 |
| 小喜 | 早期基线 ECH 开启返回 20 条；没有把它写成最终包复测。 |
| 师兄 | 旧入口不在当前 JAR。新 `csp_WexAppV7Guard` / `AppV7Dsx` 实搜返回 1 条，私有核对有有效 ID、标题和海报，标题匹配搜索词，非错误/登录提醒卡。已对远端 wex 执行带版本条件的 api/ext 单项更新，回读一致，仍为 76 源，全局 JAR 未换。 |
| 次元 | 阿里仍出现 UnknownHostException；腾讯曾连接到 HTTP 200，但该词零条结果，不能算搜索功能恢复。 |
| 指南 | 腾讯下出现纯 TCP 超时，未出现 ECH 拒绝；本轮未修复其连通性。 |
| 太狗、好盘 | 当前 JAR 缺旧入口。含原类的旧 JAR 已分别在隔离包实测，两项均在原生初始化阶段 SIGABRT，尚未进入搜索；候选被拒绝，用户配置未替换。 |

师兄更新证据：`wex-migration-applied.json`、`shixiong-content-validation.json`；搜索摘要：`wex-search-summary.json`。师兄验证范围为搜索，未宣称本次完成播放验收。

## DNS / ECH 的未解决边界

设备阿里节点曾对 GET 返回错误别名及 RCODE 3，而 POST A 查询可返回真实 IPv4，因此地址查询改为标准 POST，与 ECH 配置查询保持同一传输方式。但最终真实 JAR 的次元仍解析失败；进一步对阿里两个节点改变 DNS ID、加 no-cache 的 POST 测试仍多次得到错误响应，仅个别正确。POST 不是这个服务端异常的完整修复。

历史和本轮均捕获过真实 ECH_REJECTED，后续同源又能成功。设备阿里与腾讯返回的两份 ECH 公钥都取得了服务端接受证据，不能把不同配置 ID 直接当作过期。成功路由诊断确认目标为 Cloudflare，宿主 hosts 覆盖为空；另有 TCP 超时，和 TLS 拒绝分别记录。Android VPN 的 UID 范围覆盖原版、preview 和 sourceprobe，未发现按应用分流差异。

本轮未关闭用户 ECH，也未加入静默普通 TLS 重试。间歇 ECH 失败及部分源的网络/上游问题仍未全部解决。

隔离 sourceprobe/test 已卸载，设备临时 APK 与差异文件已清理，临时传输服务已停止。最终再次核验预览版 APK SHA-256 正确，首页 resumed；原版与预览版均保留。
