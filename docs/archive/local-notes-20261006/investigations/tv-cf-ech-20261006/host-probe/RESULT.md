# Frozen host ECH cross-domain configuration result

> 历史归档：以下内容记录原会话当时的计划、判断和状态；其中的任务指令不代表现在要执行的操作。当前入口见 [文档首页](../../../../../README.md)。

Completed UTC: 2026-10-06T00:18:11.541080+00:00
Repository HEAD: 97663d22f32a9777ebf406aabf2d8f3ea2224569

- Unmodified production ConscryptEchSocketFactory, EchDnsResolver, EchDnsParser; experimental ConfigProvider selects crypto.cloudflare.com as the configuration owner.
- Same DoH provider https://dns.alidns.com/dns-query: crypto.cloudflare.com has a 71-byte ECHConfigList; www.cloudflare.com and cloudflare.com each return no_ech / 0 bytes.
- Each target was requested at its own https://<target>/cdn-cgi/trace URL, HTTP Host, TLS inner hostname and certificate hostname.
- Both OFF phases: HTTP 200, same-response sni=plaintext and h=<target>.
- Both borrowed-config ON phases: HTTP 200, same-response sni=encrypted and h=<target>; acceptedEvidence=true is derived from this response, never from config retrieval.
- All four phases: TLS 1.3, ALPN h2 / HTTP_2, connected Socket address within live Cloudflare official IPv4/IPv6 ranges.
- Normal certificate chain trust uses org.conscrypt.TrustManagerImpl initialized with the system store. OkHttp default hostname verification remains enabled. No permissive trust or custom hostname verifier; global providers unchanged.
- The OFF base socket also uses a local Conscrypt provider with normal system trust. This removes the JDK trust-manager GENERIC-auth incompatibility and permits a clean ECH OFF/ON comparison; no global provider registration.
- No handshake, certificate, hostname, HTTP or ECH failure occurred in the frozen four phases. Initial auxiliary Python IP-list fetch without trailing slash returned HTTP 403; fetching official URLs with curl and trailing slash succeeded.
- Scope is HOST OpenJDK + Conscrypt 2.7.0 + OkHttp 5.5.0; this proves acceptance for these two public targets now, not every Cloudflare site or Android deployment.
- No repository or device changes. No target IP addresses or complete trace response bodies recorded.

Evidence: alidns-result.json. Source and result SHA-256 values: manifest.json.
