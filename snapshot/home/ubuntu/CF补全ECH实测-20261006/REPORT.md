# Cloudflare 补全 ECH 实测 · 2026-10-06

**这次才补上了无自有 ECH 记录的 Cloudflare 站点。** 昨天的理解和实现只使用目标自己发布的配置，没有完成这项补全。现在生产自动路径已通过主机和 Android 实际握手验证；完整 CI 预览 APK 已安装并启动首页，11 个设置/数据库文件在首次启动前的哈希与安装前及本轮初始快照一致。

目标明确没有自有配置时，使用同一选定 DoH 查询 A/AAAA；至少有一个地址，且返回地址全部属于已知 Cloudflare 官方网段，才获取 `crypto.cloudflare.com` 的共享配置交给 TLS。原 URL、HTTP Host、TLS 内层域名、证书身份和连接地址保持原逻辑。非 CF、混合地址或查询失败不强用共享配置；已尝试 ECH 的握手失败不自动明文重试。识别依据是所选 DNS 的结果，不等于观察到了代理实际连接的目标 IP。

使用现有入口：**设置 → 应用 → DoH 选择腾讯 → 开启 ECH**，对新连接生效，无需另一个补全开关。本设备已完整验证腾讯；360 只验证了 DNS 查询及地址归属，不能写成 Android ECH 已通过。阿里对不同域名的回答存在差异，见下方记录。

## 实际握手结果

每项接受结论都来自同一次请求的服务端 `sni=encrypted`，不把“拿到配置”当成接受证据。

| 环境与路径 | 目标 / DoH | OFF → ON | 结果 |
| --- | --- | --- | --- |
| HOST 生产自动路径 | `www.cloudflare.com`、`cloudflare.com` / 阿里 | `plaintext` → `encrypted` | 两个无自有配置站点通过 |
| Android 独立直连 | `www.cloudflare.com` / 腾讯 | `plaintext` → `encrypted` | 补全通过，正常证书及域名校验 |
| Android 共享 Spider 客户端 | `www.cloudflare.com` / 腾讯 | `plaintext` → `encrypted` | 宿主集成通过，设置及 DoH 恢复 |
| Android 独立直连 | `cloudflare.com` / 阿里 | `plaintext` → `encrypted` | 第二个无自有配置站点通过 |
| Android HTTP CONNECT 认证夹具 | `www.cloudflare.com` / 默认 Cloudflare | `plaintext` → `encrypted` | 补全及预期代理路由通过 |
| Android 独立 / 共享直连回归 | `crypto.cloudflare.com` / 阿里 | `plaintext` → `encrypted` | 两条原生配置路径通过 |

补全组均先核实目标自有配置为 0 / `no_ech`；独立 ON 路径记录 `cloudflare_ech_fallback` / 71 字节。成功请求均 HTTP 200、TLS 1.3、HTTP/2，ON 使用实际 Conscrypt socket、ALPN `h2`，同响应域名匹配。Android 四轮 instrumentation 分别为 `OK (2 tests)`、`OK (1 test)`、`OK (1 test)`、`OK (2 tests)`，共六组 OFF/ON 对照。

独立路径使用正常系统信任和默认域名校验，未安装全局 provider。共享路径沿用项目既有证书策略，只证明 ECH 集成，不能证明其证书校验安全。HTTP CONNECT 结果仅覆盖所用夹具，不推广为任意代理；独立网络栈的 JAR、WebView、native MPV 也不在本次覆盖范围。

非 CF 负例 `www.google.com` 返回 `cloudflare_non_cf_address` / 0 字节。`example.com` 当前的 A/AAAA 均属 CF，不能用作非 CF 负例。首轮设备 AliDNS 对 `www.cloudflare.com` 返回了非 CF 的 A 和 CF 的 AAAA，独立路径正确拒绝借用；共享请求为 `ConnectException`。这轮失败保留在 [android-initial-attempt-summary.json](android-initial-attempt-summary.json)，没有被后续腾讯成功结果覆盖。

证据：[HOST 摘要](host-summary.json)、[Android 六组摘要](android-summary.json)、[设备 DNS 归属摘要](device-dns-summary.json)。均不含原始 DNS 包、完整 trace、目标地址或代理凭据。

## 构建、测试与安装

生产功能提交 `c39e777`，完整 CI [37395642484](https://github.com/wobuhui666/TV/actions/runs/37395642484) 的 preview、sourceprobe 构建均成功，sourceprobe 网络单测及 mobile 编译步骤成功。主机组合单测 **86 项通过**。

提交 `ed85d19` 只增加 Android 测试中的腾讯/360及 apex 参数，未修改生产应用；新版测试 APK 来自完整 CI [37398331981](https://github.com/wobuhui666/TV/actions/runs/37398331981)。实际使用的应用 APK 仍是 `c39e777` 完整 CI 产物；交付时仅重签名，非签名内容一致，文件哈希已核验。详见 [CI 摘要](ci-summary.json)、[测试 APK CI 摘要](probe-ci-summary.json)、[交付文件核验](signed-build-summary.json)。

本轮安装包：[CF-ECH-preview-arm64.apk](CF-ECH-preview-arm64.apk)。来源为 `c39e777` 完整 CI 预览应用，仅重签名；设备独立核验与交付文件的 SHA-256 一致：

```text
c64106b75cd7febc17fa77548d64d6f3698484001ea49072c75cc156a91857ee
```

安装成功并已启动首页。11 个设置/数据库文件在首次启动前与安装前及本轮初始快照逐一相同；未替用户修改 ECH 偏好，原应用包未触碰。交付 APK 使用同盘硬链接保存。详见 [安装核验](installation-summary.json)。昨天的报告和局部编译更新 APK 保留在 `/home/ubuntu/ECH实测-20261005/`，与本轮完整 CI 包区分。

正式 release [build-37399371147](https://github.com/wobuhui666/TV/releases/tag/build-37399371147) 已成功发布，源码为 `ed85d19`，提供 ARM64 和 ARMv7 APK。当前设备安装完成的包是上面已核验的完整 CI 预览包。总状态见 [validation-summary.json](validation-summary.json)。
