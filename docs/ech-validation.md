# ECH 验证说明

## 默认行为

- ECH 默认关闭，开启后使用官方 `org.conscrypt:conscrypt-android:2.7.0`。
- `EchDnsResolver` 查询 DNS HTTPS 记录（类型 65），从中获取 ECHConfigList。
- 普通 DNS 选择“自动”时，ECH 查询默认使用 `https://1.1.1.1/dns-query`。
- 已选择 DoH 时，ECH 查询复用当前 DoH 选择。
- 未找到配置或 DoH 查询失败时，回退到原 TLS 路径。
- 已尝试 ECH 的握手本身失败时，不自动降级重试；可关闭 ECH 后重新连接。
- 因此该开关不提供“必须使用 ECH，否则拒绝连接”的强制隐私模式。

## 覆盖范围与证据

- 共享 `Spider.client()`、项目共享 OkHttp，以及使用该宿主客户端的 JAR 请求可经过此路径。
- 无法控制自带网络栈的独立 JAR，也不覆盖 MPV、WebView 的独立请求。
- 获取到配置、调用 `setEchConfigList` 成功或普通 TLS 握手成功，都不等于 ECH 已被服务端接受。
- Conscrypt 2.7.0 没有公开的 SSLSocket ECH accepted 查询 API。
- 当前探针请求发布 ECH 配置的 `crypto.cloudflare.com/cdn-cgi/trace`，以同一 HTTPS 响应中的 Cloudflare trace `sni=encrypted` 为服务端证据。
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
它使用固定任务夹具 HTTP CONNECT 代理，仅对该夹具的 Basic 挑战回应一次，不向源站发送代理凭据。
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

## 当前验证状态

已核验源码、官方发布 API，以及离线 TrustManager 拒绝传播。
设备上的 ECH 接受结果尚未确认为 PASS；必须读取本次完整报告后再判断。
HTTP CONNECT 认证夹具的测试代码已提供，设备代理结果仍待实测；SOCKS 未测。
ECH 不保证通过 Cloudflare 风控、验证码或其他站点访问策略。

设备初测确认 Cloudflare DoH 的 TCP/TLS 连接受当前网络限制；阿里 DoH 的正常证书验证、GET/POST 均可达。应用继续尊重用户的 DoH 选择，不会在失败时偷偷更换提供商。`www.cloudflare.com` 当时未发布 ECH 配置，不能作为开启组的成功目标；测试已改用实际提供配置的 `crypto.cloudflare.com`。这些初测只定位 DNS 条件，尚不代表 ECH 已成功。
