# ECH / 次元 DNS 只读调查（2026-10-06 02:30–02:44 UTC）

本调查不操作设备、不改项目代码。只读设备代理生成的脱敏报告、历史日志及公开 DNS；没有保存配置凭据、查询词、完整源 URL 或媒体标题到本报告。

## 已确认

- 小龙 `csp_ManJuAiHuoLongGuard` 的实际单源搜索，ECH OFF / ON 各返回 9 条。ON 的 `djapi.999888456.xyz` 使用 `org.conscrypt.Java8EngineSocket` 并返回 HTTP 200。小喜 ON 报告返回 20 条。目前这些操作未复现旧日志里的 ECH_REJECTED。
- 小龙目标本身发布 HTTPS65 ECH，因此不会进入借用配置分支。AliDNS、腾讯 DoH、Cloudflare DoH 当前均返回与 `crypto.cloudflare.com` 相同的 71 字节 ECHConfigList，SHA256 `91b8271208d717986460da53a9704b4a5199d582b172c715f98567e247218e47`、configId 145、TTL 299–300 秒。A/AAAA 三方一致且均在 CF 范围。
- `wex.json` 顶层没有 hosts 重写。未发现配置把上述域名改到其他地址的证据。
- 次元 `www.cycani.org` 当前主机侧三个 DoH 均 NOERROR：CNAME `cycani.alidns-2.com` → A `45.89.219.195`；AAAA 和 HTTPS 仅 CNAME、无对应终点记录。此地址不属 CF，原实现不会借 ECH。
- 用生产 OkHttp 格式（qid=0、flags=0x100、无 EDNS）对次元做 3 DoH × A/AAAA × GET/POST，12 次均 NOERROR；A 都有 45.89.219.195。主机侧不能复现设备 UnknownHostException。
- 直查权威：`seth.ns.cloudflare.com` 返回 www.cycani.org A 的 AA+NOERROR CNAME；`alina.ns.cloudflare.com` 返回 HTTPS 的同样结果。`vip7.alidns.com` 返回 cycani.alidns-2.com A 的 AA+NOERROR 45.89.219.195、TTL1；`vip8.alidns.com` 返回 AAAA NOERROR/NODATA（SOA）。当前不是全网 NXDOMAIN/SERVFAIL。

## 旧 ECH 错误能证明什么

历史日志 thread 55811 的 ECH_REJECTED suppressed 栈落到 ManJuAiHuoLong；thread 55810 落到 ManJuAiXiFan。最终 IPv6 ENETUNREACH 地址是 `2606:4700:3033::6815:3379` / `2606:4700:3034::ac43:b42d`，与当前 djapi 的 AAAA 一致。但旧错误没有打印实际 hostname；CF 地址可能复用，故只能把错误归属到这两个 source，不能严格证实每次被拒绝都来自 djapi，也不能证明原因是密钥轮换。

## 代码核对

- Conscrypt Android 2.7.0 的 ConscryptEngine 会读取 `ssl.getEchRetryConfigs()`，但 `Platform.wrapEchRejectedException(...)` 直接 `return e`；EchRejectedException 仅 message 构造器。公开 API 没有可用 retry bytes，当前不能按标准 retry-config 重试。
- OkHttp 5.5 `StateMachineDnsCall`：RCODE 0 正常；RCODE2 产生带 `DNS server failure` 的 UnknownHostException；其他非零 RCODE 产生无 message UnknownHostException。
- `Dns.Call.execute()` 只要已累计至少一条 IP 即返回成功，忽略其他查询局部异常。因此 AAAA NOERROR/NODATA 不会抹掉成功 A。
- DNS reader 跳过 CNAME RR，但可以读取同一 answer 内最终 A。仅 CNAME、无 A 时不会主动继续追 alias；最终报 DNS returned no addresses。设备当前无 message 例外更符合非零 RCODE，需抓设备实际 wire 验证。
- 生产 `OkDns` 使用 DoH 默认 GET；ECH resolver 使用 POST。设备 curl 证据需要同时匹配 GET/POST 和生产 query wire，不能只用 POST 判断 GET 一定正常。

## 建议与限制

先完成已证实的 DoH 主线程关闭连接修复。保留 ECH 开关语义，不因历史未复现异常自动明文降级。

若后续确证陈旧 ECH 密钥，可在特定 EchRejectedException 后仅清除该域及 donor 的 ECH 缓存，下次新连接重新 DoH；若做一次同请求重试，也应保持 ECH，且只有新配置确实不同才重试。当前未验证轮换，建议不将这种推测修复混入崩溃补丁。

次元需继续由设备代理对照 ECH OFF / 腾讯 DoH，以及设备 GET/POST wire RCODE。这里主机 DNS 成功不能代表设备路径成功；也不能称它是已死域名。

## 证据

- `probe-0-false.json` / `probe-0-true.json` / `probe-1-true.json` / `probe-2-true.json`（设备代理产生）
- `host-dns-*.json`（主机公开 DNS 响应摘要）
- `cycani-get-post-host.json`（12 次匹配生产查询格式的对照）
- `okhttp-dnsoverhttps-5.5.0-sources.jar`（Maven Central 源码）
- `/tmp/okhttp-5.5.0-api-audit/okhttp-jvm-5.5.0-sources.jar`
- `/tmp/conscrypt-ech-api/conscrypt-android-2.7.0-sources.jar`

## 补充（02:47 UTC）

设备代理后续确认次元 AliDNS 的 ECH ON/OFF 均 UnknownHost；腾讯 ON 已到达目标 HTTP200，但该查询没有搜索结果，不能声称源功能修好了。

另用完整抓取脚本主机侧复核 Ali/腾讯 GET/POST，各次保存 HTTP status、remote IP、cache headers；默认主机 Ali 使用 IPv6，强制 IPv4（223.5.5.5 / 223.6.6.6）也全部 NOERROR。因此主机侧没有 GET/POST 或 IPv4/IPv6 错误分歧；必须等待设备自己的 wire 证据。

补充证据：`cycani-host-wire/summary.json`、`cycani-host-ipv4-wire/summary.json`；可供设备代理运行的脚本 `cycani_wire_matrix.py`（本调查从未自行在设备执行）。

## 新 APK 复现后的补充（03:01 UTC）

`probe-fixed-0-true.json` 已在完整新 APK 的真实小龙搜索复现 `djapi.999888456.xyz` 的 `org.conscrypt.EchRejectedException`（同时有 IPv6 ConnectException、SocketTimeoutException），约41秒失败。旧成功测试 DNS 地址数4，新失败数6；现有探针只记数量，待设备确定额外地址。

即时主机三DoH native/donor HTTPS65仍同 configId145、hash91b82712…；Ali/腾讯IPv4 DoH GET/POST A/AAAA/HTTPS也一致，无公钥轮换证据。

使用既有未修改生产 EchDnsResolver/ConscryptEchSocketFactory 和 OpenJDK Conscrypt2.7、默认严格证书与主机名校验，在主机验证 djapi /cdn-cgi/trace：普通TLS回 plaintext，ECH回 encrypted。分别强制两个IPv4 104.21.51.121 / 172.67.180.45，以及默认IPv6路径，均HTTP200且ECH接受。这里仅证明当前公钥及这三个主机路由可用，不证明设备网络路径，也不替代源搜索。

证据：`djapi-host-ipv4-wire/summary.json`、`host-djapi-probe/alidns-result.json`、`host-djapi-probe/104.21.51.121-result.json`、`host-djapi-probe/172.67.180.45-result.json`。

`IN_FLIGHT_LIMIT=4` 忙时返回null会跳过ECH，其并发语义值得独立评估，但不可能直接产生这次明确的EchRejectedException，暂不以此解释已复现错误。

## ECH 拒绝后的密钥诊断手段

主机上将有效 ECHConfigList 的 configId 故意改错，独立原始 SSLSocket 握手按预期抛 `org.conscrypt.EchRejectedException`。此时通过仅测试反射 `socket.engine → ssl → echHandshakeBuilder → retryConfigs`，取得服务端给出的71字节重试列表。无需JNI、无需改Conscrypt或生产代码。

证据 `host-djapi-probe/intentional-retry-config.json`：有效配置hash91b82712…；故意错误hash5e8b55b4…；服务端retryhash6d7acc87…。服务端retry配置不同于DNS当前配置本身不证明DNS配置坏（当前DNS配置已在同一路由成功），只能作为设备失败时的进一步诊断。当前无公开API，正式产品不应未经评估就依赖这些私有反射字段；这里限独立测试。

## 最终冻结结论（设备 routev2 后）

有效 `djapi-device-wire/summary.json` 显示设备 AliDNS 返回 configId101/hash6d7acc87…，腾讯返回145/hash91b82712…。两家各自GET/POST一致，目标A/AAAA均相同4地址。这是可观察到的DNS回答差异，不能直接称为密钥过期。

经批准只增加一次新变量验证：主机直接使用设备捕获的101配置（`device-ech-config-id101.bin`，不做freshDNS），强制与已成功145相同的IPv4 104.21.51.121，原生产factory/Conscrypt2.7，启用HTTPS端点证书主机名校验。结果握手成功、HTTP200、trace sni=encrypted。因此101/145在当前已测路由都可用。证据 `host-djapi-probe/captured-device-config101.json`。

设备代理随后 routev2 真实小龙搜索成功9条：实际DNS4地址；OkDns hosts map前后为空；DIRECT连104.21.51.121，Conscrypt且HTTP200；172.67.180.45的另一条竞速连接被取消，SocketException属于该次竞速结果。此前失败时6地址的组成尚未捕获，间歇ECH拒绝仍未复现到可唯一定位的具体route+config组合。

当前不修改生产TLS策略、不自动关闭ECH、不替换101、不据此添加post(true)。已完成崩溃修复可独立交付；真实JAR网络问题应如实记录为间歇失败，并保留本轮有效证据。若再复现，最小补充是同设备、同目标IP、捕获config的原始握手及失败retryhash，避免用当前成功测试冒充故障已消除。

旧设备curl矩阵因Android不能打开/dev/stderr而产生exit23/空wire的记录全部无效；仅以修正版stdout分离HTTP头后生成的`djapi-device-wire`作为DNS证据。
