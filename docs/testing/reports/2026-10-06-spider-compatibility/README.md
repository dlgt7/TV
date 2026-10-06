# 新旧爬虫接口与动漫源实测

> 记录范围：以下结论对应报告所列日期、提交和设备；后续版本应重新核对。公开证据见本目录；原文提到的 APK、私有文件及临时脚本不随本次归档。来源见 [导入清单](../../../archive/local-notes-20261006/IMPORTS.json)。

FongMi/CatVodSpider 已发布新 SDK；核对时 FongMi/TV 公开源码仍为 2026-09-28 的 `c616c0aa3613e87529791587a9f71b78c278c991`，未接入新 SDK。本次在用户 TV 分支中补齐接口，并保留旧 Java、QuickJS、Python 通道。

设备为 Tailscale `[REDACTED_DEVICE_IP]:5555` 的 Samsung SM-F900F / Android 13。测试使用独立 `com.fongmi.android.tv.sourceprobe` 包。

| 检查 | 实际结果 |
| --- | --- |
| Java 新接口、真实 Python/Chaquopy、新旧 DEX 加载 | Android 3 项测试全部通过；包含 init 前注入、Local 隔离、JSON/二进制/缓存/会话、旧 requests、旧 JAR 辅助类优先加载与资源释放 |
| QuickJS 新旧桥接 | 真实 JNI 测试通过：对象/Promise/字符串返回，旧 req/http/local，新 net、缓存、会话、下载、代理字节流 |
| Girigiri，同一 JAR + 旧 App | 分类、分页、搜索、空结果、详情、两线路、分集解析通过；Exo 与 MPV 各播放 2 集通过 |
| Girigiri，同一 JAR + 新 App | 同上全部通过；Exo 与 MPV 各播放 2 集通过 |
| Sorani，同一 JAR + 新旧 App | 两版均可加载。设备到镜像地址池的 TCP 连接超时，未进入 TLS/ECH，未完成设备播放 |
| Sorani，主机与真实网站 | 分类、列表分页、搜索、详情、分集取票、HLS、16 字节 AES 密钥和 TS 分片通过；相同 IP 的 API GET 返回 HTTP 200 |
| 原 WEX（师兄）JAR | 新 App 实测加载与搜索通过，返回 1 条结果 |
| DoH 切换回归 | 主线程 DoH/ECH 切换通过，关闭 2 个空闲 TLS 连接，活动响应正常完成 |
| 原红果 Python | 95 个步骤中 92 PASS、1 OBSERVATION、1 NOT_APPLICABLE、1 FAIL；Exo/MPV 各连续播放 2 集通过 |
| 原哔哩哔哩 JS + 原配 JAR + 原模块地址 | 旧版前轮接口通过；新版 CI5 严格检查通过：10 类、两页各 99 条有效视频、搜索 35 条，真实搜索项详情及有效非空播放地址通过（parse=0） |
| 原腾讯 JS + 原配 JAR + 原模块地址 | 新版 CI5：7 类、搜索 9 条有效视频；真实搜索项详情及有效非空播放地址通过（parse=0）。分类两页均只有 1 条占位项、有效视频数为 0，单独记为分类空 |

红果唯一失败项是 `search.longQueryBoundary`：34 字查询被上游返回 HTTP 500；2026-10-05 的旧报告也记录了相同失败。普通搜索及其他功能通过。不能将此报告称为所有边界场景均通过。

Sorani 的网络分层检查使用设备原生 curl 与独立 TCP 连接，对多个镜像 IP 均发生 TCP 超时；设备访问腾讯的对照连接正常。主机访问相同 IP 可以完成 TCP、TLS 和真实 API GET。该结果说明本轮设备网络路径不可达，不能据此断言具体路由或封锁原因，也不能通过切换 ECH 解决尚未建立的 TCP 连接。

前轮腾讯详情错误来自测试误选 no_data 占位项，已撤回对腾讯真实视频详情故障的归因。CI5 排除占位/空 ID/文件夹，改从真实搜索结果取视频后，详情和 player 均通过，返回 1 个有效非空 HTTP(S) 地址；哔哩哔哩同样通过。腾讯分类两页当前没有真实视频，需要保留这一限制。上述 JS 验证覆盖接口与播放地址结构，没有执行腾讯或哔哩哔哩实际媒体解码；Girigiri 与红果的 Exo/MPV 播放已单独实测。

## 构建与可追溯信息

- App CI：[37428214817](https://github.com/wobuhui666/TV/actions/runs/37428214817)，生产提交 `e4cc2003d7a770942a978f30b85b54149aceeb67`；TV 两个 APK 对、手机版编译、CatVod/QuickJS JVM 测试及 Python/JS 协议测试通过。
- 最终旧源测试 CI：[37446014814](https://github.com/wobuhui666/TV/actions/runs/37446014814)，测试提交 `29d3449f2486473122372ca41451d008df7efd2f`；两个 APK 对、共享网络/QuickJS JVM、Python/JS 协议及手机版编译全部通过。CI5 仅更新测试；加载原配 JAR、保留原模块地址，并从真实搜索结果验证详情与有效非空播放地址。实测运行于上述 CI2 App，腾讯和哔哩哔哩均通过；CI4 的占位详情失败记录已被此结果取代。不同 CI 构建的 APK 不保证字节相同。
- CI5 设备测试 APK SHA-256：`e65d44c22feae1cf5828a1dc2f594b13041380dbc7bc67e92e58bd7784703590`；与下载的 CI5 测试 APK 比较，全部 14 个非签名 ZIP 项内容相同。
- 源标准构建：[37428099682](https://github.com/wobuhui666/TV/actions/runs/37428099682)，Windows 执行 `gradlew.bat spiderJar`，R8 和仓库 JAR 检查通过。
- JAR SHA-256：`dc0e5fcd5f95308a0a169fa31383e75bc213c38b5d9378138e13dacdd3f20ab4`。
- 旧测试 App SHA-256：`ed0f873e140bf7a8854b16c7129bbe60f8bd07554b73501874d34364ef0dfaf9`。
- 新测试 App SHA-256：`0c18eff7da3a77867ca2fb2334c3e2fa78dd9405e89cc1b38f6ec7e341b7f3f6`。
- 预览 APK SHA-256：`118bf002138858ea0c5fb93b101e964a390f4f35132a34b294fae0767c845106`。APK 仅重新签名，并逐项核对所有非签名文件内容；预览包已安装到三星；首次启动前，11 个配置/数据库文件逐一核对完全一致；HomeActivity 正常恢复且未出现 AndroidRuntime 崩溃。

正式发布：[build-37446584066](https://github.com/wobuhui666/TV/releases/tag/build-37446584066)，主分支 `ui/apple-tv-redesign` 已更新至 `29d3449f2`。正式构建、ARM64/ARMv7 APK 上传和 Release 发布全部成功。相对 CI2 的生产代码没有变更，只修正上述测试文件；本设备安装的是已完成实测的独立预览包。

测试收尾已卸载 sourceprobe 和它的 test 包，移除临时恢复 APK/差量文件；用户预览包及数据保留，已恢复 HomeActivity 并确认进程运行。

## 两个源的配置

[可导入配置](https://raw.githubusercontent.com/wobuhui666/TV/7506be6cbe414a6b53605c21e0cb15556b3193ac/json/ciallo.json) · [JAR](https://raw.githubusercontent.com/wobuhui666/TV/7506be6cbe414a6b53605c21e0cb15556b3193ac/jar/custom_spider.jar)

设备已成功读取该配置。jsdmirror 对配置 JSON 返回 200，但对 JAR 返回 403，因此本次提供已验证的 GitHub Raw 地址。原导出目录中的 `ciallo.json` 与 `custom_spider.jar` 可配套使用；本次仅收录配置 JSON，JAR 通过上方固定提交链接获取。

后续发布更新：CatVodSpider 写权限已生效，源码/JAR 已推送到该仓库的 `feat/ciallo-anime-sources` 分支（`0cfe42a0`），两个源已加入远端 wex.json，使用主机与三星均实际下载通过的 gh-proxy.com。旧的 TV 临时发布链接仍保留。新报告位于 [发布与配置验证.md](../2026-10-06-ciallo-publishing/README.md)。源代码继续使用旧 `Spider.client()` 及旧 JSON 格式，不要求新接口。

本目录不包含原配置凭据、完整私人源脚本或带临时票据的播放地址。公开结果记录测试状态和计数；相关私有材料仅保留在工作目录用于排查。
