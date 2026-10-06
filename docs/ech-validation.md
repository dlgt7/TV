# ECH 验证说明

## 默认行为

- ECH 默认关闭，开启后使用官方 `org.conscrypt:conscrypt-android:2.7.0`。
- `EchDnsResolver` 查询 DNS HTTPS 记录（类型 65），从中获取 ECHConfigList。
- 未选择 DoH（界面显示“系统”）时，ECH 查询默认使用 `https://1.1.1.1/dns-query`。
- 已选择 DoH 时，ECH 查询复用当前 DoH 选择。
- 优先使用目标自己发布的 ECH 配置。目标明确没有 HTTPS 记录或没有 ECH 参数时，使用同一 DoH 查询其 A/AAAA；至少有一个地址且所有已返回地址均属于 Cloudflare 已知官方网段，才使用 `crypto.cloudflare.com` 的共享 ECH 配置。
- 只补充交给 TLS 的 ECHConfigList，不改变目标 URL、HTTP Host、TLS 内层域名、证书身份或连接地址。DoH 服务端的公开记录不会被修改。
- DoH 查询失败、无法确认 Cloudflare 归属、取不到共享配置或目标端口不是 443 时，回退到原 TLS 路径。
- 已尝试 ECH 的握手本身失败时，不自动降级重试；可关闭 ECH 后重新连接。
- 因此该开关不提供“必须使用 ECH，否则拒绝连接”的强制隐私模式。

## 覆盖范围与证据

- 共享 `Spider.client()`、项目共享 OkHttp，以及通过受控宿主 API 使用该客户端的 JAR 请求可经过此路径。
- 本次共享路径实测覆盖宿主客户端集成，不能推广为所有 JAR 均使用 ECH。独立或 shaded 网络栈的 JAR、native MPV、WebView 不在覆盖范围内。
- 获取到配置、调用 `setEchConfigList` 成功或普通 TLS 握手成功，都不等于 ECH 已被服务端接受。
- Conscrypt 2.7.0 没有公开的 SSLSocket ECH accepted 查询 API。
- 当前探针默认请求没有发布 ECH 配置的 `www.cloudflare.com/cdn-cgi/trace`，先核实目标自有配置缺失，再以同一 HTTPS 响应中的 Cloudflare trace `sni=encrypted` 证明补全生效。`ech_target_mode=published` 切换到有自有配置的 `crypto.cloudflare.com` 进行回归。
- 同时核对实际 Conscrypt socket、TLS 1.3/ALPN、请求目标及预期路由。
- 关闭组的预期结果为 `sni=plaintext`，开启组为 `sni=encrypted`。

## 两条验证路径

测试类为 `com.fongmi.android.tv.test.ConscryptEchProbeTest`，包含以下验证：

| 测试方法 | 验证用途 |
| --- | --- |
| `factoryOffAndOnProducePlaintextAndEncryptedServerEvidence` | 独立 factory 与真实 resolver，比较关闭/开启组 |
| `sharedSpiderClientUsesProductionEchAndRestoresSettings` | 检查共享 Spider 客户端集成与配置恢复 |

独立路径使用正常证书链和主机名验证，不增加证书放行逻辑。
另有离线拒绝型 TrustManager 检查，验证 basic、Socket、SSLEngine 三个服务端入口的拒绝异常传播。
离线委托检查不能替代真实 TLS 证书验证测试。

共享路径保留项目既有的 `trustAll` 与 `hostnameVerifier(true)` 行为。
该路径只能证明 ECH 集成，不能证明证书验证安全；不得将其结果用于上述安全结论。
测试基于 `shared.newBuilder()`，保留 SSLFactory、DNS 和 interceptors。
为隔离测试连接，另建 connection pool 与 dispatcher，禁止重定向，并添加阻止凭据发送的网络 guard。

## 隔离与参数

- 测试只允许运行在 `sourceprobe` 或 `netprobe` 隔离应用包。
- 共享测试保存并恢复 ECH 设置键，包括原先不存在该键的状态。
- 同时保存并恢复 DoH snapshot，不把测试选择保留为用户配置。
- 不用测试结果覆盖生产配置，也不为测试安装全局 TLS provider。

Instrumentation 参数 `ech_doh_mode`：

| 值 | 测试使用的 ECH DoH |
| --- | --- |
| `default`（默认） | `https://1.1.1.1/dns-query` |
| `cloudflare` | 固定 `https://cloudflare-dns.com/dns-query` |
| `alidns` | 固定 `https://dns.alidns.com/dns-query`，共享测试会同步选择这个 DoH |

可选代理参数 `ech_proxy_mode=fixture` 只用于独立方法；不传或 `direct` 为直连。
它使用固定任务夹具 HTTP CONNECT 代理，仅对该夹具的 Basic 挑战回应，每次连接拒绝重复认证，不向源站发送代理凭据。
DoH bootstrap 与目标请求均走该夹具，并检查实际 HTTP 代理路由及认证发生。
共享方法拒绝 `fixture`，这项测试不能证明用户全局代理规则或其他代理兼容性。
独立方法总上限 60 秒；共享方法上限 90 秒。代理夹具必须由测试操作者先启动。

示例选择参数（追加到正常 instrumentation 命令，不是独立命令）：

```text
-e class com.fongmi.android.tv.test.ConscryptEchProbeTest#factoryOffAndOnProducePlaintextAndEncryptedServerEvidence -e ech_doh_mode default -e ech_proxy_mode fixture
-e class com.fongmi.android.tv.test.ConscryptEchProbeTest#sharedSpiderClientUsesProductionEchAndRestoresSettings -e ech_doh_mode default
```

报告位于隔离应用的 external files 目录下：

- `ech-validation/conscrypt-probe.json`：独立路径结果。
- `ech-validation/conscrypt-shared-probe.json`：共享客户端结果。
- 报告仅保存状态和必要计数，不输出 trace 正文、地址信息或凭据。

## 2026-10-06 Cloudflare 共享配置补全

上一版只处理站点自己发布的 ECH，未覆盖“没有发布配置但 CF 边缘能解密”的站点。本次增加自动识别与补全。

- `EchDnsResolver.resolve()` 保留仅查询目标自有配置的语义，便于获取共享来源与核验前提；宿主 TLS 路径改用 `resolveWithCloudflareFallback()`。
- A/AAAA 与 ECH 配置均从当前选定的 DoH 获取，不增加针对目标的系统 DNS 查询。CNAME 有界跟随，只采纳与问题域名关联的 answer 地址；错误、混合非 CF 地址或全空结果均不借用。
- 匹配 2026-10-06 的 Cloudflare 官方 IPv4/IPv6 网段快照，支持 IPv4-mapped IPv6；BYOIP、专用地址等不在快照内的情况可能不被识别。
- 整次补全共用 6 秒/8 次 DNS 查询预算；两种查询模式共用最多 4 个进行中任务、128 条 LRU。借用条目有效期受目标缺失记录、地址和共享配置各自期限约束。
- HTTP/SOCKS 代理仍按原配置连接。这里判断的是所选 DoH 给出的域名归属，不宣称看到了代理实际连接的目标地址；保留原站身份和证书策略，握手拒绝不自动改为明文重试。

主机已验证生产解析器自动处理 `www.cloudflare.com`、`cloudflare.com`：目标自有配置为零，`cloudflare_ech_fallback` 返回共享配置，同响应由 `plaintext` 变为 `encrypted`，TLS 1.3/HTTP2、证书验证和原域名保持正常。Android 本轮结果及新版安装待实际验证后记录，不沿用下面上一版结果冒充。

## 2026-10-05 实测结果（上一版）

完整 CI 构建的 `sourceprobe` 隔离包已在 Android 13 / ARM64 设备完成直连及 HTTP CONNECT 夹具实测。源码为 `ae072fb82fbc862073b6a5050ee665114875851c`，CI run `37364398536`（attempt 2），sourceprobe 构建成功。71 项网络单测通过：DNS parser 32、resolver 14、factory 11、proxy 14；factory 新增 Android 反射式上下文信任检查，原先合计为 70 项。

| 路径 | DoH | 关闭 / 开启服务端证据 | 结果 |
| --- | --- | --- | --- |
| 独立客户端，正常证书及主机名验证，直连 | 阿里 | `plaintext` / `encrypted` | PASS |
| 真实共享 `Spider.client()`，直连 | 阿里 | `plaintext` / `encrypted` | PASS |
| 独立客户端，HTTP CONNECT 认证夹具 | 默认 Cloudflare | `plaintext` / `encrypted` | PASS |

三项实测响应均为 HTTP 200、TLS 1.3、HTTP/2；开启组均为实际 Conscrypt socket、ALPN `h2`。独立路径经真实 resolver 取得 71 字节 ECHConfigList。共享路径保留原 factory、DNS、应用 interceptors，并恢复 ECH 设置及 DoH；它没有单独插桩配置字节数。直连 instrumentation 为 `OK (2 tests)`。代理 instrumentation 为 `OK (1 test)`；实际 HTTP 代理路由匹配，关闭组认证回应 1 次，开启组 2 次（包含 DoH），源站凭据 guard 通过。该结果仅覆盖所用 HTTP CONNECT 夹具。

设置界面验证通过：缺省设置键不存在时为关闭；遥控器可聚焦、切换，重启保留切换状态，Material 使用说明可打开；测试结束已恢复关闭。`ech-settings.png` 与 `ech-info.png` 是切换过程截图，截图中的开启状态不代表默认值。

修复了 Conscrypt EngineSocket 对 `X509ExtendedTrustManager` 的内部包装会丢失反射式 ECH policy 的问题。适配器改为实现 `X509TrustManager`，保留 public Socket/SSLEngine 重载，Android 仍可反射调用上下文证书检查；未增加证书放行或全局 provider。原始 `CertificateException` 的传播有回归测试。

本设备直连 `1.1.1.1`、`1.0.0.1` 超时，`cloudflare-dns.com` 在 TLS 阶段被重置；阿里 DoH 的正常证书验证、GET/POST 均可达。应用尊重用户的 DoH 选择，不自动更换提供商。本设备使用步骤为：**设置 → 应用 → DoH 选择阿里 → 开启 ECH**，对新连接生效。

Cloudflare 站点并非全部发布 ECH 配置：本次 `www.cloudflare.com` 没有配置，`crypto.cloudflare.com` 有配置。ECH 不保证通过风控、验证码或其他站点访问策略。SOCKS、任意用户代理与全部 JAR 网络栈未验证。

## 预览包安装与构建来源

预览包已安装，设备核验 APK SHA-256 为 `b3743c93a58f1a7c6cde1ac52c420345383711aafc18ea0e721b195950d63b4a`，首页已启动。安装前后、首次启动前的 11 个偏好设置 / 数据库文件哈希全部相同，并与本次会话最初快照一致；原应用包未改动，未替用户更改 ECH 偏好设置。

安装包基于 CI run `37359756519`、源码 `4f2bbb6` 的预览产物，替换为实际从 `ae072fb` Java 源码经 javac / D8 编译得到的四个 factory 类。独立审查确认四个替换类与编译结果一致，其余 265 个同 DEX 类的规范化哈希、另 37 个 DEX 和其余非签名资源均未变，签名及对齐检查通过。这是本地源码编译更新，不是完整的新源码预览 CI 产物；完整 `ae072fb` sourceprobe CI 包已通过上述三项设备实测。

完整预览 CI 因 GitHub 托管 runner 未能启动而取消。正式 release run `37367866351` 在本次归档时仍排队，未记为正式发布成功。

脱敏报告、最终 JSON、界面截图及已安装 APK 位于 `/home/ubuntu/ECH实测-20261005/`，入口为 `REPORT.md`，APK 为 `TV-preview-ECH-arm64.apk`。报告区分完整 CI sourceprobe 证据和本地编译预览包来源；不包含 DNS 查询正文、完整 trace、设备地址或凭据。
