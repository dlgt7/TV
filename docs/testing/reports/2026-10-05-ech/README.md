# ECH 实测报告 · 2026-10-05

> 记录范围：以下结论对应报告所列日期、提交和设备；后续版本应重新核对。公开证据见本目录；原文提到的 APK、私有文件及临时脚本不随本次归档。来源见 [导入清单](../../../archive/local-notes-20261006/IMPORTS.json)。

完整 CI 构建的隔离测试包已通过 Android 13 / ARM64 设备的两项直连验证及 HTTP CONNECT 认证夹具验证：关闭 ECH 时服务端返回 `sni=plaintext`，开启后返回 `sni=encrypted`。预览包已安装并成功启动首页；安装前后、首次启动前的 11 个偏好设置 / 数据库文件哈希全部一致。

## 实测结果

目标为发布 ECH 配置的 `crypto.cloudflare.com/cdn-cgi/trace`。判定依据来自同一 HTTPS 响应的服务端 trace 字段；取得配置或完成普通 TLS 握手不足以判定 ECH 成功。

| 验证路径 | DoH | 关闭 | 开启 | 结果 |
| --- | --- | --- | --- | --- |
| 独立客户端，正常证书链和主机名验证 | 阿里 | `plaintext` | `encrypted` | PASS |
| 真实共享 `Spider.client()` | 阿里 | `plaintext` | `encrypted` | PASS |
| 独立客户端，HTTP CONNECT 认证夹具 | 默认 Cloudflare | `plaintext` | `encrypted` | PASS |

三项实测均收到 HTTP 200、TLS 1.3、HTTP/2 响应。开启组均使用实际 Conscrypt socket，ALPN 为 `h2`；独立路径通过真实生产 resolver 获取 71 字节 ECHConfigList。共享路径保留原 factory、DNS 和应用 interceptors，测试后恢复 ECH 设置及 DoH。共享路径没有插桩 ECH 配置字节数，不能把独立路径的计数当作共享计数。直连 instrumentation 为 `OK (2 tests)`。

代理 instrumentation 为 `OK (1 test)`。代理路由核验通过；关闭组认证回应 1 次，开启组 2 次（包含 DoH），开启组同样取得 71 字节配置并返回 `encrypted`。源站凭据 guard 通过。此处的默认 Cloudflare DoH 经测试代理可达，不代表本设备可以直连该服务。

独立路径未增加证书放行或自定义主机名放行。共享路径沿用项目既有信任策略（`trustAll` / `hostnameVerifier(true)`），因此其结果证明 ECH 集成，不能证明共享客户端证书校验安全。离线及反射式上下文测试确认原始证书拒绝异常继续传播。

## 本设备如何使用

**设置 → 应用 → DoH 选择阿里 → 开启 ECH**。设置影响新连接，ECH 默认关闭。

本设备网络对 Cloudflare DoH 有连接限制：`1.1.1.1`、`1.0.0.1` 的 TCP 连接超时，`cloudflare-dns.com` 的 TLS 握手被重置。阿里 DoH 的正常证书验证以及 GET/POST 请求均可用。应用尊重用户当前 DoH 选择，不会自动改用其他提供商。

Cloudflare 站点并非全部支持 ECH：本次 `www.cloudflare.com` 未发布配置，`crypto.cloudflare.com` 发布了配置。无配置、DoH 失败或目标端口不是 443 时仍走原 TLS 路径；已尝试 ECH 的握手失败时不自动降级重试。该开关不属于“必须加密，否则拒绝连接”的强制模式，也不保证绕过风控或验证码。

## 界面与范围

界面检查确认：缺省键不存在时关闭；遥控器可聚焦和切换；重启保留开启状态；Material 使用说明可打开；检查结束已恢复关闭。

[设置操作截图](ech-settings.png) · [使用说明截图](ech-info.png) · [界面验证 JSON](ech-ui-validation.json)

截图记录的是切换过程，因此显示开启；默认关闭和最终恢复关闭的依据是界面验证 JSON。

本次实测覆盖共享 OkHttp / `Spider.client()` 的宿主客户端集成。通过受控宿主 API 使用该客户端的 JAR 请求可以共用这条路径，但本次证据不构成所有 JAR 请求的实测证明。独立或 shaded 网络栈的 JAR、native MPV、WebView 不在此覆盖范围。HTTP CONNECT 夹具也不能代表任意用户代理、SOCKS 或所有代理规则。

## 修复与构建

修复了 Conscrypt EngineSocket 内部包装 `X509ExtendedTrustManager` 时丢失 ECH policy 的问题：适配器实现 `X509TrustManager` 并保留公开的 Socket / SSLEngine 检查入口，使 Android 能反射调用上下文证书验证，同时保留 ECH policy。未安装全局 provider。

网络单测由 70 项增至 71 项：DNS parser 32、resolver 14、factory 11、proxy 14；新增测试覆盖 Android 反射调用时的原始证书拒绝和上下文传递。Android 37 core API 编译检查通过。

- 源码：`ae072fb82fbc862073b6a5050ee665114875851c`
- CI：run `37364398536`，attempt 2，sourceprobe job 成功。
- 包名：`com.fongmi.android.tv.sourceprobe`。
- 本次完整 CI 包安装文件 SHA-256：`5e6d950c8c8eb73db9c8cd7785f9f4feefcea007a2e6741037b356940a5a2911`。
- [构建与签名摘要](final-build.json) 区分 CI 原产物和为隔离安装重新签名后的文件哈希。

上述三项 ECH 实测结果来自完整 CI 构建的 `sourceprobe` 包；此前的本地源码编译隔离包仅用于先行定位。最终安装的预览包来源单独说明如下。

## 预览包安装与下载

下载已安装的 ARM64 预览 APK（原引用 `TV-preview-ECH-arm64.apk`，未随本次归档）

SHA-256：`b3743c93a58f1a7c6cde1ac52c420345383711aafc18ea0e721b195950d63b4a`

设备独立核验了安装 APK 的哈希，预览首页已启动。安装前后、首次启动前的 11 个偏好设置 / 数据库文件逐文件哈希相同，也与本次会话最初快照一致。原应用包未改动，未替用户更改 ECH 偏好设置。

该预览包是 **CI `37359756519` 的 `4f2bbb6` 预览包，加上从真实 `ae072fb` Java 源码经 javac / D8 编译得到的四个 factory 类**，属于本地源码编译更新。独立审查确认替换类与编译结果一致，其余 265 个同 DEX 类的规范化哈希、另 37 个 DEX 及其余非签名资源均未变，签名和对齐检查通过。它不是完整的新源码预览 CI 产物；相同 factory 源码已在完整 CI `sourceprobe` 包上通过上述三项设备实测。

完整预览 CI 因 GitHub 托管 runner 未能启动而取消。正式 release run `37367866351` 在本次归档时仍排队，尚不能作为已完成的正式发布。

[安装与数据保留核验](preview-install-verification.json) · [预览包来源摘要](preview-build-summary.json)

## 脱敏证据

- [最终直连独立路径](final-direct-conscrypt-probe.json)
- [最终直连共享路径](final-direct-conscrypt-shared-probe.json)
- [最终 HTTP CONNECT 夹具路径](final-proxy-conscrypt-probe.json)
- [Instrumentation 结果摘要](final-instrumentation-summary.json)

本目录保存已安装 APK、必要状态、计数、构建摘要及界面截图；不收录 DNS 查询正文、完整服务端 trace、设备网络地址或凭据。APK 使用同文件系统硬链接归档，没有额外复制一份安装包。

正式发布任务第一次因 GitHub 无法分配托管 runner 而结束，未执行编译；已发起一次重试。已安装的预览版及上面的三项设备实测结果不受此影响。[发布构建状态记录](release-build-status.json)。
