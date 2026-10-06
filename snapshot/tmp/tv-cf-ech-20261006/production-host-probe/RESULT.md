# Production automatic Cloudflare ECH fallback validation

Fresh real EchDnsResolver instances use https://dns.alidns.com/dns-query. Factory, resolver, parser and address ranges are compiled unchanged from current repository sources. The temporary ConfigProvider calls resolveWithCloudflareFallback(host); it does not choose the donor or inject config bytes.

- www.cloudflare.com and cloudflare.com: native HTTPS65 returns no_ech / 0 bytes. Automatic resolution returns cloudflare_ech_fallback / 71 bytes. Both OFF trace responses report plaintext; both ON trace responses report encrypted.
- crypto.cloudflare.com: automatic resolution keeps ech_config_available / 71 bytes. OFF trace reports plaintext; ON trace reports encrypted.
- Six TLS requests all return HTTP 200, TLS 1.3, ALPN h2. Each same-response h field matches the original URL/Host/TLS inner hostname. All actual Socket addresses are inside live official Cloudflare ranges.
- System certificate trust and OkHttp default hostname validation remain enabled. Conscrypt provider is local, global provider list unchanged. OFF uses the same local Conscrypt system trust setup to avoid the host JDK GENERIC-auth issue.
- www.google.com is the confirmed non-Cloudflare negative control: A and AAAA each return eight addresses, all outside live official Cloudflare ranges; production automatic resolution returns cloudflare_non_cf_address / 0 bytes.
- Requested candidate example.com is currently Cloudflare-hosted: two A and two AAAA addresses all match the independently downloaded live official ranges. Its cloudflare_ech_fallback / 71-byte result is expected, so it is not a negative control.
- Additional candidate www.iana.org returns dns_rcode for this HTTPS65 lookup and correctly yields no config. Its separately queried A/AAAA addresses also belong to Cloudflare, so it is not a non-Cloudflare negative control.
- Combined EchDnsParserTest, AddressParserTest, EchDnsResolverTest, EchDnsResolverCloudflareTest and CloudflareAddressRangesTest: 86 tests passed. JVM test compilation uses the installed JDK because existing resolver tests use List.of; production parser Android/Java8 compatibility was verified separately earlier.

Evidence: alidns-result.json; non-cf-dns-result.json; ../combined-tests/junit.log; manifest.json with source/result SHA-256.

Scope: HOST OpenJDK with Conscrypt 2.7.0 / OkHttp 5.5.0, not Android deployment. Configuration discovery is distinct from the same-response encrypted acceptance evidence. No repository/device writes for this validation, no target IPs or full trace bodies recorded.
