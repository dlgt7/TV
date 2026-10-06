# Private investigation notes

The current wex.json and the exact cached JAR were read under the user's authorization. Remote/current and device JAR SHA256 both equal 5284ad33611932ec2596075be4a158d7ccfedfcbb6f0c43cfeee112cfcd62982. Configuration contents and raw returned search items must not be published.

## Confirmed

- Current JAR lacks the configured Guard classes for 师兄, 太狗, 好盘; ClassNotFoundException appears for all three in the user's search log. Clearing cache cannot add missing classes.
- 师兄 public-current API migration to WexAppV7Guard/AppV7Dsx was independently searched with the original keyword on the device. One actual matching media item has a nonempty ID, title and image, and is not an error card. Evidence: shixiong-content-validation.json. The response itself is only in shixiong-private-body.json.
- 小龙 and 小喜 use the host's observed shared client (real JAR loading, not a mock spider). Successful ECH-enabled requests use raw org.conscrypt sockets; successful queries returned 9 and 20 real-result counts. Direct Cloudflare trace proof is separate and cannot be inferred merely from a socket class.
- 小龙 also intermittently failed: one Ali request included EchRejectedException, another Tencent request timed out before TLS. Later the same Ali source succeeded through 104.21.51.121 with four real CF addresses and zero OkDns overrides. Neither of the two public ECH IDs alone proves a bad key.
- The device's AliDNS GET response for www.cycani.org returned NXDOMAIN with the unexpected cname.lab alias. A same-wire POST returned the valid IPv4 CNAME chain. Tencent worked via either method. See cycani-device-wire-fixed/summary.json and preserved wire packets. This directly motivates the separately implemented POST fix.
- 指南 source www.2xiaopan.top returned no content after IPv4 TCP timeouts and IPv6 unreachability; no ECH rejection was observed. ECH cannot solve a TCP connect timeout by itself.
- Android Tailscale VPN covers UID 0–99999, including the original app, preview and sourceprobe. No application-specific VPN exclusion or different UID route was found; external gateway routing remains outside this observation.

## Evidence handling

Baseline runs are in baseline-probes. The initial baseline probe-0-true JSON was unfortunately replaced by a later ADB-offline attempt; its successful 9-item result remains in the tool transcript, but the file must not be represented as preserved raw evidence. Subsequent labels fixed-, route-, shixiong-candidate- and shixiong-content- distinguish test revisions. The first malformed device DNS scripts returned transport errors and are not DNS evidence. Only directories ending in the successful wire matrix summaries should be used.

R1 full-CI DoH StrictMode test passed: two strict main-thread changes, two real idle Conscrypt sockets closed, one active response completed. The original/preview packages were never navigated or force-stopped by this diagnostic agent; preview installations and preservation checks were performed separately by root.

R2 exact full-CI bytes were reconstructed and verified as ed0f873e140bf7a8854b16c7129bbe60f8bd07554b73501874d34364ef0dfaf9, then installed. The DoH StrictMode regression passed again. 小龙 on AliDNS/ECH returned 9 normal items through 104.21.51.121 with HTTP200. 次元 still received UnknownHostException from AliDNS despite POST, so the provider-specific problem is not resolved. See the post-r2 result files.

The old JAR candidate was tested once each for 太狗 and 好盘. Both aborted the isolated process during source-init with JNI ClassLoader.loadClass called on a null object. Do not deploy it. Current original fixture JAR and the private original test plan were restored afterward; no user app or remote configuration was touched by these candidate tests.
